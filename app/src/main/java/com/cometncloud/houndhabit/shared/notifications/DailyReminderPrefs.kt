package com.cometncloud.houndhabit.shared.notifications

import android.content.Context

/**
 * SharedPreferences-backed flags for the daily training reminder.
 * Parallel to iOS UserDefaults usage in NotificationManager.swift.
 *
 * Defaults match iOS: 9:00 AM, disabled until the user toggles on.
 */
class DailyReminderPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("daily_reminder", Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var hour: Int
        get() = prefs.getInt(KEY_HOUR, 9)
        set(value) = prefs.edit().putInt(KEY_HOUR, value).apply()

    var minute: Int
        get() = prefs.getInt(KEY_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_MINUTE, value).apply()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"
    }
}
