package com.example.applauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ApiServerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var apiServer: ApplicationEngine? = null
    private val tag = "ApiServerService"

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "api_server_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "ApiServerService onCreate")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.i(tag, "Foreground notification started")
        startApiServer()
    }

    private fun startApiServer() {
        serviceScope.launch {
            try {
                Log.i(tag, "Starting Ktor API server on port 3001")
                val apiService = ApiService(applicationContext)
                apiServer = apiService.createServer()
                apiServer?.start(wait = true)
            } catch (e: Exception) {
                Log.e(tag, "API server error", e)
                stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(tag, "ApiServerService onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            apiServer?.stop(gracePeriodMillis = 1000, timeoutMillis = 3000)
            Log.i(tag, "API server stopped")
        } catch (e: Exception) {
            Log.e(tag, "Error stopping API server", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("App Launcher API")
            .setContentText("REST API running on port 3001")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "App Launcher API Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the REST API server running"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
