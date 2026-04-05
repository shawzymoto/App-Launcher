package com.example.applauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

class QuietHoursAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "QuietHoursAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val manager = QuietHoursManager(context)

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                manager.scheduleAlarms()
                if (manager.isNowInQuietHours()) {
                    QuietHoursActivity.start(context)
                } else {
                    QuietHoursActivity.stop(context)
                }
            }

            QuietHoursManager.ACTION_QUIET_HOURS_START -> {
                Log.i(TAG, "Quiet hours start alarm fired")
                QuietHoursActivity.start(context)
                manager.scheduleAlarms()
            }

            QuietHoursManager.ACTION_QUIET_HOURS_END -> {
                Log.i(TAG, "Quiet hours end alarm fired")
                wakeScreen(context)
                manager.queuePendingResumeAppLaunch()
                QuietHoursActivity.stop(context)
                manager.scheduleAlarms()
            }
        }
    }

    private fun wakeScreen(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "app-launcher:quiet-hours-wake"
        )
        wakeLock.acquire(3000)
    }
}
