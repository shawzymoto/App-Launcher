package com.example.applauncher

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null,
    val error: String? = null
)

@Serializable
data class HealthData(val port: Int, val version: String)

@Serializable
data class LaunchData(val packageName: String)

@Serializable
data class LaunchWithOptionsData(val packageName: String, val action: String)

@Serializable
data class AppsData(val apps: List<AppListResponse>)

@Serializable
data class ConfigData(
    val port: Int,
    val apiKeyRequired: Boolean,
    val supportedApps: List<SupportedApp>
)

@Serializable
data class LaunchRequest(
    val packageName: String,
    val action: String? = null,
    val extras: Map<String, String>? = null,
    val deepLink: String? = null
)

@Serializable
data class AppListResponse(
    val appName: String,
    val packageName: String,
    val supported: Boolean,
    val supportedActions: List<String> = emptyList()
)

@Serializable
data class SupportedApp(
    val name: String,
    val packageName: String,
    val deepLinkScheme: String? = null,
    val supportedActions: List<String> = emptyList(),
    val description: String? = null
)

@Serializable
data class QuietHoursStatusData(
    val enabled: Boolean,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val activeNow: Boolean
)

@Serializable
data class QuietHoursUpdateRequest(
    val enabled: Boolean? = null,
    val startHour: Int? = null,
    val startMinute: Int? = null,
    val endHour: Int? = null,
    val endMinute: Int? = null
)

@Serializable
data class QuietHoursEnabledRequest(
    val enabled: Boolean
)
