package com.example.applauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var appLauncher: AppLauncher
    private lateinit var appManager: AppManager
    private lateinit var listView: ListView
    private lateinit var statusTextView: TextView

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appLauncher = AppLauncher(this)
        appManager = AppManager(this)

        listView = findViewById(R.id.app_list_view)
        statusTextView = findViewById(R.id.status_text_view)

        ensureNotificationPermissionAndStartService()
        loadAndDisplayApps()
    }

    private fun ensureNotificationPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_POST_NOTIFICATIONS
                )
                return
            }
        }

        startForegroundApiService()
    }

    private fun startForegroundApiService() {
        try {
            val intent = Intent(this, ApiServerService::class.java)
            startForegroundService(intent)
            statusTextView.text = "✓ REST API starting on port 3001\nAPI Key: ${ApiKeyManager(this).getApiKey()}"
        } catch (e: Exception) {
            statusTextView.text = "✗ Failed to start REST API\n${e.message}"
            Toast.makeText(this, "Failed to start API service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_POST_NOTIFICATIONS) {
            startForegroundApiService()
        }
    }

    private fun loadAndDisplayApps() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.Default) {
                appManager.getInstalledApps()
            }
            val appNames = apps.map { it.name }

            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, appNames)
            listView.adapter = adapter

            listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                val selectedApp = apps[position]
                if (appLauncher.isAppInstalled(selectedApp.packageName)) {
                    appLauncher.launchApp(selectedApp.packageName)
                }
            }
        }
    }
}
