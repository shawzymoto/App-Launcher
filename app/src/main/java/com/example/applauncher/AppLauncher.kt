package com.example.applauncher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

class AppLauncher(private val context: Context) {

    private val tag = "AppLauncher"
    private val launchFlags =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
    @Volatile
    private var lastLaunchError: String? = null

    fun launchApp(packageName: String): Boolean {
        lastLaunchError = null

        if (!canStartActivitiesNow()) {
            lastLaunchError = "BACKGROUND_ACTIVITY_START_BLOCKED"
            Log.w(tag, "Background activity start likely blocked by Android for package: $packageName")
            return false
        }

        val packageManager = context.packageManager

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(launchFlags)
            if (tryStartIntent(launchIntent, "default launch intent", packageName)) {
                return true
            }
        }

        // Fallback for apps that don't expose a default launch intent.
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            `package` = packageName
        }
        val launcherActivities = packageManager.queryIntentActivities(launcherIntent, 0)
        for (resolveInfo in launcherActivities) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(activityInfo.packageName, activityInfo.name)
                addFlags(launchFlags)
            }
            if (tryStartIntent(explicitIntent, "launcher activity", packageName)) {
                return true
            }
        }

        // Android TV style launcher fallback.
        val leanbackIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            `package` = packageName
        }
        val leanbackActivities = packageManager.queryIntentActivities(leanbackIntent, 0)
        for (resolveInfo in leanbackActivities) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                setClassName(activityInfo.packageName, activityInfo.name)
                addFlags(launchFlags)
            }
            if (tryStartIntent(explicitIntent, "leanback activity", packageName)) {
                return true
            }
        }

        // Some apps expose an INFO entry point instead of launcher categories.
        val infoIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_INFO)
            `package` = packageName
        }
        val infoActivities = packageManager.queryIntentActivities(infoIntent, 0)
        for (resolveInfo in infoActivities) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_INFO)
                setClassName(activityInfo.packageName, activityInfo.name)
                addFlags(launchFlags)
            }
            if (tryStartIntent(explicitIntent, "info activity", packageName)) {
                return true
            }
        }

        Log.w(tag, "No launchable activity found for package: $packageName")
        lastLaunchError = "NO_LAUNCHABLE_ACTIVITY"
        showError("Could not find launchable activity for $packageName")
        return false
    }

    private fun tryStartIntent(intent: Intent, source: String, packageName: String): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: SecurityException) {
            val message = e.message ?: ""
            lastLaunchError = if (message.contains("Background activity", ignoreCase = true)) {
                "BACKGROUND_ACTIVITY_START_BLOCKED"
            } else {
                "SECURITY_EXCEPTION"
            }
            Log.w(tag, "Failed launch attempt for $packageName from $source", e)
            false
        } catch (e: Exception) {
            lastLaunchError = "${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
            Log.w(tag, "Failed launch attempt for $packageName from $source", e)
            false
        }
    }

    fun getLastLaunchError(): String? = lastLaunchError

    fun canStartActivitiesNow(): Boolean {
        val appState = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appState)
        return appState.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    fun setLastLaunchError(error: String) {
        lastLaunchError = error
    }

    fun hasLaunchableActivity(packageName: String): Boolean {
        if (context.packageManager.getLaunchIntentForPackage(packageName) != null) {
            return true
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            `package` = packageName
        }
        if (context.packageManager.queryIntentActivities(launcherIntent, 0).isNotEmpty()) {
            return true
        }

        val leanbackIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            `package` = packageName
        }
        return context.packageManager.queryIntentActivities(leanbackIntent, 0).isNotEmpty()
    }

    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun showError(message: String) {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } else {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Unable to show toast: $message", e)
        }
    }
}
