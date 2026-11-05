package com.pfms.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pfms.app.data.local.dao.CryptoTradeDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import com.pfms.app.domain.repository.AuthRepository
import java.util.concurrent.TimeUnit

/** Upload-only trade queue. See INSTALLATION.md for multi-device reconciliation limitations. */
@HiltWorker
class CryptoTradeSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: CryptoTradeDao,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val auth: AuthRepository
): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = auth.currentUser.first()?.uid ?: return Result.success()
        if (uid != firebaseAuth.currentUser?.uid) return Result.retry()
        return try {
            dao.pending(uid).forEach { t ->
                if (firebaseAuth.currentUser?.uid != uid) return Result.retry()
                val ref = firestore.collection("users").document(uid)
                    .collection("cryptoTrades").document(t.id)
                val data = mapOf(
                    "id" to t.id, "userId" to uid, "asset" to t.asset,
                    "side" to t.side, "quantity" to t.quantity,
                    "grossLkrMinor" to t.grossLkrMinor, "feeLkrMinor" to t.feeLkrMinor,
                    "date" to t.date, "createdAt" to t.createdAt,
                    "originalCurrency" to t.originalCurrency,
                    "originalGrossMinor" to t.originalGrossMinor,
                    "originalFeeMinor" to t.originalFeeMinor,
                    "exchangeRate" to t.exchangeRate, "note" to t.note,
                    "realizedLkrMinor" to t.realizedLkrMinor
                )
                // Immutable trade IDs: do not overwrite a server trade with different data.
                firestore.runTransaction { tx ->
                    val snap = tx.get(ref)
                    if (!snap.exists()) tx.set(ref, data)
                    else require(snap.data == data) { "Server trade differs from local trade: ${t.id}" }
                }.await()
                dao.markSynced(uid, t.id)
            }
            Result.success()
        } catch (error: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<CryptoTradeSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "crypto_trade_pending_sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request
            )
        }
    }
}
