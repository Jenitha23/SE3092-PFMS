package com.pfms.app.data.reminder

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pfms.app.MainActivity
import com.pfms.app.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ReminderNotification(val key: String, val title: String, val body: String, val destination: String)

interface ReminderNotifier {
    fun access(): NotificationAccess
    fun createChannels()
    fun canPost(type: ReminderType): Boolean
    fun post(uid: String, type: ReminderType, notification: ReminderNotification): Boolean
    fun cancelType(uid: String, type: ReminderType)
    fun cancelOtherUsers(uid: String?)
}

@Singleton
class AndroidReminderNotifier @Inject constructor(@param:ApplicationContext private val context: Context) : ReminderNotifier {
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    override fun access(): NotificationAccess {
        val runtimeMissing = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        return NotificationAccess(!runtimeMissing && NotificationManagerCompat.from(context).areNotificationsEnabled(), runtimeMissing)
    }
    override fun createChannels() {
        ReminderType.entries.distinctBy(::channelId).forEach { type ->
            manager.createNotificationChannel(NotificationChannel(channelId(type),
                if (type == ReminderType.DUE_RECURRING) "Expense & Subscription Reminders" else type.title,
                NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    override fun canPost(type: ReminderType) = access().allowed &&
        manager.getNotificationChannel(channelId(type))?.importance?.let { it != NotificationManager.IMPORTANCE_NONE } == true

    override fun post(uid: String, type: ReminderType, notification: ReminderNotification): Boolean {
        if (!canPost(type)) return false
        val tag = prefix(uid, type) + ReminderPreferencesStore.identity(notification.key)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("pfms://reminders/$tag")
            putExtra(EXTRA_DESTINATION_ROUTE, notification.destination)
        }
        val pending = PendingIntent.getActivity(context, tag.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val built = NotificationCompat.Builder(context, channelId(type))
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(notification.title)
            .setContentText(notification.body).setContentIntent(pending).setAutoCancel(true)
            .setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        return try { manager.notify(tag, 0, built); true } catch (_: SecurityException) { false }
    }
    override fun cancelType(uid: String, type: ReminderType) {
        manager.activeNotifications.filter { it.tag?.startsWith(prefix(uid, type)) == true }
            .forEach { manager.cancel(it.tag, it.id) }
    }
    override fun cancelOtherUsers(uid: String?) {
        val keep = uid?.let { "$TAG_PREFIX${ReminderPreferencesStore.identity(it)}_" }
        manager.activeNotifications.filter {
            // Retire untagged legacy notifications too; they cannot be safely attributed after sign-out.
            (it.tag?.startsWith(TAG_PREFIX) == true && (keep == null || !it.tag.startsWith(keep))) ||
                (it.tag == null && it.notification.channelId == LEGACY_CHANNEL)
        }.forEach { manager.cancel(it.tag, it.id) }
    }
    companion object {
        const val LEGACY_CHANNEL = "pfms_expense_reminders"
        const val EXTRA_DESTINATION_ROUTE = "extra_destination_route"
        private const val TAG_PREFIX = "pfms_reminder_"
        fun prefix(uid: String, type: ReminderType) = "$TAG_PREFIX${ReminderPreferencesStore.identity(uid)}_${type.key}_"
        // Reusing the existing channel also preserves users' previous OS-level blocking choices.
        fun channelId(type: ReminderType) = if (type == ReminderType.DUE_RECURRING || type == ReminderType.SUBSCRIPTION_REVIEW)
            LEGACY_CHANNEL else "pfms_${type.key}_reminders"
    }
}
