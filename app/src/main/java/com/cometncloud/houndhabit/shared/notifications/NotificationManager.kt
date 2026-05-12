package com.cometncloud.houndhabit.shared.notifications

import android.app.NotificationChannel
import android.app.NotificationManager as SystemNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cometncloud.houndhabit.MainActivity
import com.cometncloud.houndhabit.R

/**
 * Owner of the daily-reminder notification channel and the actual
 * NotificationCompat.Builder used for posting. Parallel to the iOS
 * NotificationManager, but scheduling lives in a separate file (WorkManager
 * Worker) since Android does not have a calendar-trigger primitive.
 *
 * Channel registration is safe to repeat; we do it from MainActivity once.
 */
object NotificationManager {
    private const val TAG = "Notify"

    const val CHANNEL_DAILY_REMINDERS = "daily_reminders"
    const val NOTIFICATION_ID_DAILY_REMINDER = 1001

    fun ensureChannels(context: Context) {
        val sys = context.getSystemService(SystemNotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_DAILY_REMINDERS,
            "Daily Training Reminder",
            SystemNotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Daily prompt to log a training session."
        }
        sys.createNotificationChannel(channel)
    }

    /**
     * Posts the daily-reminder notification right now. Used by both the
     * Settings "Test notification" button and the WorkManager Worker (step 3).
     * No-ops silently if the runtime POST_NOTIFICATIONS permission is not
     * granted on Android 13+.
     */
    fun showDailyReminder(context: Context) {
        Log.d(TAG, "showDailyReminder")
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Daily Training Reminder")
            .setContentText("Time to train! Log a session in Hound Habit.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .build()

        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) {
            Log.d(TAG, "notifications disabled by user / permission")
            return
        }
        try {
            compat.notify(NOTIFICATION_ID_DAILY_REMINDER, notification)
        } catch (se: SecurityException) {
            // Pre-grant on API 33+ before runtime permission is asked.
            Log.d(TAG, "notify denied: ${se.message}")
        }
    }
}
