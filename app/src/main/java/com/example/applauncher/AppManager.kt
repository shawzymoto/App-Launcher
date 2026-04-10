package com.example.applauncher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable?
)

class AppManager(private val context: Context) {

    fun getInstalledApps(): List<AppInfo> {
        val packageManager = context.packageManager
        val apps = mutableListOf<AppInfo>()

        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val launcherActivities = packageManager.queryIntentActivities(launcherIntent, 0)
            val seenPackages = mutableSetOf<String>()

            for (activity in launcherActivities) {
                try {
                    val activityInfo = activity.activityInfo ?: continue
                    val packageName = activityInfo.packageName
                    if (!seenPackages.add(packageName)) continue

                    val appName = activity.loadLabel(packageManager)?.toString()
                        ?: packageManager.getApplicationLabel(activityInfo.applicationInfo).toString()
                    val appIcon = activity.loadIcon(packageManager)

                    apps.add(
                        AppInfo(
                            name = appName,
                            packageName = packageName,
                            icon = appIcon
                        )
                    )
                } catch (e: Exception) {
                    // Skip apps with missing metadata.
                }
            }

            apps.sortBy { it.name.lowercase() }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return apps
    }
}
