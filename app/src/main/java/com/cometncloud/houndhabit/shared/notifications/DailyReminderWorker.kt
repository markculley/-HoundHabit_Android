package com.cometncloud.houndhabit.shared.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager worker that fires the daily reminder. Enqueued by
 * [DailyReminderScheduler] as a periodic 24h job; WorkManager handles
 * the repeat cadence and reboot persistence.
 */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "fire")
        NotificationManager.showDailyReminder(applicationContext)
        return Result.success()
    }

    companion object {
        private const val TAG = "DailyReminderWorker"
    }
}
