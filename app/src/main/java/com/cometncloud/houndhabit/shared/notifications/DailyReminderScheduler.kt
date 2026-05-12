package com.cometncloud.houndhabit.shared.notifications

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules / cancels the daily reminder via WorkManager. Periodic work
 * survives reboots automatically — no boot-completed receiver needed.
 *
 * The fire time is approximate (WorkManager batches jobs); for a daily
 * reminder this matches the iOS UNCalendarNotificationTrigger behavior
 * closely enough.
 */
object DailyReminderScheduler {
    private const val TAG = "ReminderScheduler"
    private const val UNIQUE_WORK_NAME = "daily_training_reminder"

    /**
     * Schedules a periodic 24h job, replacing any existing one. The first
     * fire happens at the next [hour]:[minute] from now (today if still in
     * the future, otherwise tomorrow).
     */
    fun schedule(context: Context, hour: Int, minute: Int) {
        val delayMs = msUntilNext(hour, minute)
        Log.d(TAG, "schedule ${hour.pad()}:${minute.pad()} (next fire in ${delayMs / 60_000}m)")
        val work = PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            work,
        )
    }

    fun cancel(context: Context) {
        Log.d(TAG, "cancel")
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /** Re-runs [schedule] if the user previously enabled the reminder. */
    fun rescheduleIfNeeded(context: Context) {
        val prefs = DailyReminderPrefs(context)
        if (!prefs.isEnabled) return
        schedule(context, prefs.hour, prefs.minute)
    }

    private fun msUntilNext(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return target.timeInMillis - now.timeInMillis
    }

    private fun Int.pad() = toString().padStart(2, '0')
}
