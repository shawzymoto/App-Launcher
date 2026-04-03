package com.example.applauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

class ApiService(private val context: Context) {
    private val apiKeyManager = ApiKeyManager(context)
    private val appLauncher = AppLauncher(context)
    private val appManager = AppManager(context)
    private val tag = "ApiService"
    
    companion object {
        private const val API_PORT = 3001
        private const val API_KEY_HEADER = "X-API-Key"
    }

    fun createServer() = embeddedServer(CIO, port = API_PORT) {
        configureSerialization()
        configureRouting()
    }

    private fun Application.configureSerialization() {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            })
        }
    }

    private fun Application.configureRouting() {
        routing {
            // Health check endpoint
            get("/health") {
                call.respondText(
                    "{\"success\":true,\"message\":\"API Service is running\",\"data\":{\"port\":$API_PORT,\"version\":\"1.0\"}}",
                    contentType = io.ktor.http.ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            }

            // List installed launchable apps
            get("/api/apps") {
                if (!call.verifyApiKey()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiResponse<Unit>(
                            success = false,
                            message = "Unauthorized: Invalid or missing API key",
                            error = "INVALID_API_KEY"
                        )
                    )
                    return@get
                }

                try {
                    val appList = appManager.getInstalledApps()
                        .filter { appLauncher.hasLaunchableActivity(it.packageName) }
                        .map { app ->
                        AppListResponse(
                            appName = app.name,
                            packageName = app.packageName,
                            supported = true,
                            supportedActions = emptyList()
                        )
                    }
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(
                            success = true,
                            message = "Installed launchable apps retrieved",
                            data = AppsData(apps = appList)
                        )
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Error listing apps", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(
                            success = false,
                            message = "Error retrieving apps",
                            error = e.message
                        )
                    )
                }
            }

            // Launch an app (simple, no parameters)
            post("/api/launch/{packageName}") {
                if (!call.verifyApiKey()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiResponse<Unit>(
                            success = false,
                            message = "Unauthorized: Invalid or missing API key",
                            error = "INVALID_API_KEY"
                        )
                    )
                    return@post
                }

                val packageName = call.parameters["packageName"] ?: run {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(
                            success = false,
                            message = "Missing packageName",
                            error = "INVALID_REQUEST"
                        )
                    )
                    return@post
                }

                try {
                    val launched = appLauncher.launchApp(packageName)
                    if (launched) {
                        Log.i(tag, "Successfully launched app: $packageName")
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse(
                                success = true,
                                message = "App launched successfully",
                                data = LaunchData(packageName = packageName)
                            )
                        )
                    } else {
                        val launchError = appLauncher.getLastLaunchError() ?: "LAUNCH_FAILED"
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Unit>(
                                success = false,
                                message = "Failed to launch app",
                                error = launchError
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error launching app: $packageName", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(
                            success = false,
                            message = "Error launching app",
                            error = e.message ?: "UNKNOWN_ERROR"
                        )
                    )
                }
            }

            // Launch app with deep link or intent extras
            post("/api/launch") {
                if (!call.verifyApiKey()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiResponse<Unit>(
                            success = false,
                            message = "Unauthorized: Invalid or missing API key",
                            error = "INVALID_API_KEY"
                        )
                    )
                    return@post
                }

                try {
                    val request = call.receive<LaunchRequest>()

                    val launched = launchAppWithOptions(request)
                    if (launched) {
                        Log.i(tag, "Successfully launched app with options: ${request.packageName}")
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse(
                                success = true,
                                message = "App launched with specified options",
                                data = LaunchWithOptionsData(
                                    packageName = request.packageName,
                                    action = request.action ?: "default"
                                )
                            )
                        )
                    } else {
                        val launchError = appLauncher.getLastLaunchError() ?: "LAUNCH_FAILED"
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Unit>(
                                success = false,
                                message = "Failed to launch app",
                                error = launchError
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error with launch request", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(
                            success = false,
                            message = "Invalid request format",
                            error = e.message ?: "INVALID_REQUEST"
                        )
                    )
                }
            }

            // Get API configuration
            get("/api/config") {
                if (!call.verifyApiKey()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiResponse<Unit>(
                            success = false,
                            message = "Unauthorized",
                            error = "INVALID_API_KEY"
                        )
                    )
                    return@get
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        message = "Configuration retrieved",
                        data = ConfigData(
                            port = API_PORT,
                            apiKeyRequired = true,
                            supportedApps = appManager.getInstalledApps().map {
                                SupportedApp(
                                    name = it.name,
                                    packageName = it.packageName,
                                    deepLinkScheme = null,
                                    supportedActions = emptyList(),
                                    description = "Installed app"
                                )
                            }
                        )
                    )
                )
            }
        }
    }

    private fun io.ktor.server.application.ApplicationCall.verifyApiKey(): Boolean {
        val apiKey = request.headers[API_KEY_HEADER] ?: return false
        return apiKeyManager.verifyApiKey(apiKey)
    }

    private fun launchAppWithOptions(request: LaunchRequest): Boolean {
        return try {
            if (!appLauncher.canStartActivitiesNow()) {
                appLauncher.setLastLaunchError("BACKGROUND_ACTIVITY_START_BLOCKED")
                Log.w(tag, "Blocked launch from background state for package: ${request.packageName}")
                return false
            }

            when {
                request.deepLink != null -> {
                    val deepLinkUri = Uri.parse(request.deepLink)

                    // Try deep link constrained to the target package first.
                    val packageScopedIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = deepLinkUri
                        setPackage(request.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    }

                    val packageScopedResolvable =
                        packageScopedIntent.resolveActivity(context.packageManager) != null
                    if (packageScopedResolvable) {
                        context.startActivity(packageScopedIntent)
                        return true
                    }

                    // Some apps register deep links but not with explicit package scoping.
                    val unscopedIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = deepLinkUri
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    }
                    val unscopedResolvable =
                        unscopedIntent.resolveActivity(context.packageManager) != null
                    if (unscopedResolvable) {
                        context.startActivity(unscopedIntent)
                        return true
                    }

                    // Fallback to opening the app normally if deep link isn't resolvable.
                    appLauncher.launchApp(request.packageName)
                }
                request.action != null -> {
                    // Launch with specific action
                    val intent = Intent(request.action).apply {
                        setPackage(request.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        request.extras?.forEach { (key, value) ->
                            putExtra(key, value)
                        }
                    }
                    context.startActivity(intent)
                    true
                }
                else -> {
                    // Simple launch
                    appLauncher.launchApp(request.packageName)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error launching app with options", e)
            false
        }
    }
}
