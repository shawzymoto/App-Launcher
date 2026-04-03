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

            // List all supported apps
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
                    val appList = AppConfig.SUPPORTED_APPS.map { app ->
                        AppListResponse(
                            appName = app.name,
                            packageName = app.packageName,
                            supported = appLauncher.isAppInstalled(app.packageName),
                            supportedActions = app.supportedActions
                        )
                    }
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(
                            success = true,
                            message = "Supported apps retrieved",
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
                    if (!AppConfig.isSupportedApp(packageName)) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(
                                success = false,
                                message = "App is not in supported apps list",
                                error = "UNSUPPORTED_APP"
                            )
                        )
                        return@post
                    }

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
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Unit>(
                                success = false,
                                message = "Failed to launch app",
                                error = "LAUNCH_FAILED"
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

                    if (!AppConfig.isSupportedApp(request.packageName)) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<Unit>(
                                success = false,
                                message = "App is not in supported apps list",
                                error = "UNSUPPORTED_APP"
                            )
                        )
                        return@post
                    }

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
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Unit>(
                                success = false,
                                message = "Failed to launch app",
                                error = "LAUNCH_FAILED"
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
                            supportedApps = AppConfig.SUPPORTED_APPS
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
            when {
                request.deepLink != null -> {
                    // Launch using deep link
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(request.deepLink)
                        setPackage(request.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }
                request.action != null -> {
                    // Launch with specific action
                    val intent = Intent(request.action).apply {
                        setPackage(request.packageName)
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
