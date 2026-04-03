package com.example.applauncher

import android.content.Context

object AppConfig {
    val SUPPORTED_APPS = listOf(
        SupportedApp(
            name = "Immich Frame",
            packageName = "app.immich",
            deepLinkScheme = "immich://",
            supportedActions = listOf("view", "refresh"),
            description = "Opens Immich photo frame application"
        ),
        SupportedApp(
            name = "Unifi Protect",
            packageName = "com.ubnt.android.protect",
            deepLinkScheme = "unifi-protect://",
            supportedActions = listOf("camera", "live-view", "event"),
            description = "Opens Unifi Protect security app, optionally to a specific camera view"
        )
    )

    fun getSupportedApp(packageName: String): SupportedApp? {
        return SUPPORTED_APPS.firstOrNull { it.packageName == packageName }
    }

    fun isSupportedApp(packageName: String): Boolean {
        return SUPPORTED_APPS.any { it.packageName == packageName }
    }

    fun getAppByName(appName: String): SupportedApp? {
        return SUPPORTED_APPS.firstOrNull {
            it.name.equals(appName, ignoreCase = true)
        }
    }
}
