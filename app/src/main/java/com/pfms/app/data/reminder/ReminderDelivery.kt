package com.pfms.app.data.reminder

import com.pfms.app.domain.model.ReminderType
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.usecase.ReminderSchedulePolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject

enum class ReminderRunResult { COMPLETE, RETRY }

/** Every delivery path, including old queued workers, must pass these final guards. */
class ReminderDelivery @Inject constructor(
    private val store: ReminderPreferencesStore,
    private val auth: AuthRepository,
    private val notifier: ReminderNotifier,
    private val content: ReminderContentSource,
    private val clock: Clock
) {
    suspend fun run(uid: String?, key: String?, revision: String?, notBefore: Long): ReminderRunResult {
        val type = ReminderType.fromKey(key) ?: return ReminderRunResult.COMPLETE
        if (uid.isNullOrBlank() || revision.isNullOrBlank() || notBefore <= 0) return ReminderRunResult.COMPLETE
        try {
            if (auth.currentUser.first()?.uid != uid) return ReminderRunResult.COMPLETE
            val schedule = store.read(uid)[type]
            if (!schedule.enabled || schedule.revision != revision) return ReminderRunResult.COMPLETE
            if (clock.millis() < notBefore || ReminderSchedulePolicy.deliverySlot(clock.instant(), type, schedule) == null)
                return ReminderRunResult.RETRY
            notifier.createChannels()
            if (!notifier.canPost(type)) return ReminderRunResult.COMPLETE
            val candidates = content.candidates(uid, type)
            for (candidate in candidates) {
                val outcome = store.locked {
                    currentCoroutineContext().ensureActive()
                    if (auth.currentUser.first()?.uid != uid) return@locked ReminderRunResult.COMPLETE
                    val latest = store.read(uid)[type]
                    if (!latest.enabled || latest.revision != revision || !notifier.canPost(type)) return@locked ReminderRunResult.COMPLETE
                    val slot = ReminderSchedulePolicy.deliverySlot(clock.instant(), type, latest)
                        ?: return@locked ReminderRunResult.RETRY
                    if (clock.millis() < notBefore) return@locked ReminderRunResult.RETRY
                    if (store.claim(uid, type, candidate.key, slot)) {
                        try {
                            currentCoroutineContext().ensureActive()
                            if (auth.currentUser.first()?.uid != uid || !notifier.canPost(type)) {
                                store.release(uid, type, candidate.key, slot)
                                return@locked ReminderRunResult.COMPLETE
                            }
                            if (!notifier.post(uid, type, candidate)) {
                                store.release(uid, type, candidate.key, slot)
                                return@locked ReminderRunResult.RETRY
                            }
                        } catch (error: Exception) {
                            store.release(uid, type, candidate.key, slot)
                            throw error
                        }
                    }
                    ReminderRunResult.COMPLETE
                }
                if (outcome == ReminderRunResult.RETRY) return outcome
            }
            return ReminderRunResult.COMPLETE
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { return ReminderRunResult.RETRY }
    }
}
