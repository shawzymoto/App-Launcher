package com.example.applauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.app.role.RoleManager
import android.widget.Button
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
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
    private lateinit var launcherModeStatusTextView: TextView
    private lateinit var setLauncherButton: Button

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 1001
    }

    private val homeRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateLauncherModeStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appLauncher = AppLauncher(this)
        appManager = AppManager(this)

        listView = findViewById(R.id.app_list_view)
        statusTextView = findViewById(R.id.status_text_view)
        launcherModeStatusTextView = findViewById(R.id.launcher_mode_status_text_view)
        setLauncherButton = findViewById(R.id.set_launcher_button)

        setLauncherButton.setOnClickListener {
            requestLauncherRole()
        }

        ensureNotificationPermissionAndStartService()
        updateLauncherModeStatus()
        loadAndDisplayApps()
    }

    override fun onResume() {
        super.onResume()
        updateLauncherModeStatus()
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

    private fun requestLauncherRole() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                        Toast.makeText(this, getString(R.string.launcher_already_default), Toast.LENGTH_SHORT).show()
                        return
                    }
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    homeRoleLauncher.launch(intent)
                    return
                }
            }

            // Fallback for older Android versions and devices without role API support.
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open launcher settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateLauncherModeStatus() {
        val isDefaultLauncher = isDefaultHomeApp()
        if (isDefaultLauncher) {
            launcherModeStatusTextView.text = getString(R.string.launcher_mode_enabled)
            setLauncherButton.text = getString(R.string.launcher_is_default_button)
        } else {
            launcherModeStatusTextView.text = getString(R.string.launcher_mode_not_enabled)
            setLauncherButton.text = getString(R.string.enable_launcher_mode_button)
        }
    }

    private fun isDefaultHomeApp(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val currentHomePackage = resolveInfo?.activityInfo?.packageName
        return currentHomePackage == packageName
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
