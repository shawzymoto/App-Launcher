package com.example.applauncher

import android.content.Context
import android.content.pm.ApplicationInfo
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
            val installedApps = packageManager.getInstalledApplications(0)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 } // Exclude system apps
                .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }

            for (app in installedApps) {
                try {
                    val appName = packageManager.getApplicationLabel(app).toString()
                    val appIcon = packageManager.getApplicationIcon(app)
                    apps.add(
                        AppInfo(
                            name = appName,
                            packageName = app.packageName,
                            icon = appIcon
                        )
                    )
                } catch (e: Exception) {
                    // Skip apps with missing metadata.
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return apps
    }
}
