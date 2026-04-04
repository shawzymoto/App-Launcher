package com.example.applauncher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

data class QuietHoursSettings(
    val enabled: Boolean,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
)

class QuietHoursManager(private val context: Context) {

    companion object {
        private const val TAG = "QuietHoursManager"
        const val ACTION_QUIET_HOURS_START = "com.example.applauncher.action.QUIET_HOURS_START"
        const val ACTION_QUIET_HOURS_END = "com.example.applauncher.action.QUIET_HOURS_END"

        private const val PREFS_NAME = "quiet_hours_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_START_HOUR = "start_hour"
        private const val KEY_START_MINUTE = "start_minute"
        private const val KEY_END_HOUR = "end_hour"
        private const val KEY_END_MINUTE = "end_minute"
        private const val KEY_RESUME_APP_PACKAGE = "resume_app_package"

        private const val REQUEST_START = 4001
        private const val REQUEST_END = 4002
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSettings(): QuietHoursSettings {
        return QuietHoursSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            startHour = prefs.getInt(KEY_START_HOUR, 22),
            startMinute = prefs.getInt(KEY_START_MINUTE, 0),
            endHour = prefs.getInt(KEY_END_HOUR, 7),
            endMinute = prefs.getInt(KEY_END_MINUTE, 0)
        )
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setStartTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_START_HOUR, hour)
            .putInt(KEY_START_MINUTE, minute)
            .apply()
    }

    fun setEndTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_END_HOUR, hour)
            .putInt(KEY_END_MINUTE, minute)
            .apply()
    }

    fun setResumeAppPackageName(packageName: String?) {
        prefs.edit().apply {
            if (packageName.isNullOrBlank()) {
                remove(KEY_RESUME_APP_PACKAGE)
            } else {
                putString(KEY_RESUME_APP_PACKAGE, packageName)
            }
        }.apply()
    }

    fun getResumeAppPackageName(): String? {
        return prefs.getString(KEY_RESUME_APP_PACKAGE, null)
    }

    fun isNowInQuietHours(settings: QuietHoursSettings = getSettings()): Boolean {
        if (!settings.enabled) return false

        val nowMinutes = currentMinutesOfDay()
        val startMinutes = settings.startHour * 60 + settings.startMinute
        val endMinutes = settings.endHour * 60 + settings.endMinute

        // Equal start/end means full-day quiet mode.
        if (startMinutes == endMinutes) return true

        return if (startMinutes < endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    fun scheduleAlarms() {
        val settings = getSettings()
        if (!settings.enabled) {
            cancelAlarms()
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val startAt = nextTriggerAt(settings.startHour, settings.startMinute)
        val endAt = nextTriggerAt(settings.endHour, settings.endMinute)

        try {
            val exactAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC,
                    startAt,
                    startPendingIntent()
                )

                // Wake at quiet-hours end so screen can come back on schedule.
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    endAt,
                    endPendingIntent()
                )
            } else {
                Log.w(TAG, "Exact alarms not permitted; using inexact idle alarms for quiet hours")
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC,
                    startAt,
                    startPendingIntent()
                )
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    endAt,
                    endPendingIntent()
                )
            }
        } catch (se: SecurityException) {
            // Some OEM builds can still throw even when canScheduleExactAlarms() is unreliable.
            Log.w(TAG, "Alarm scheduling restricted; falling back to inexact alarms", se)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC,
                startAt,
                startPendingIntent()
            )
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                endAt,
                endPendingIntent()
            )
        }
    }

    fun cancelAlarms() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(startPendingIntent())
        alarmManager.cancel(endPendingIntent())
    }

    private fun startPendingIntent(): PendingIntent {
        val intent = Intent(context, QuietHoursAlarmReceiver::class.java).apply {
            action = ACTION_QUIET_HOURS_START
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_START,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun endPendingIntent(): PendingIntent {
        val intent = Intent(context, QuietHoursAlarmReceiver::class.java).apply {
            action = ACTION_QUIET_HOURS_END
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_END,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextTriggerAt(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now) || timeInMillis == now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return trigger.timeInMillis
    }

    private fun currentMinutesOfDay(): Int {
        val now = Calendar.getInstance()
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    }
}
