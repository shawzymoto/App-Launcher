package com.example.applauncher

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.app.role.RoleManager
import android.util.Log
import androidx.appcompat.app.AlertDialog
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.text.DateFormat
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private var pendingResumeLaunchAttempts = 0
    private var pendingResumeRetryScheduled = false

    private lateinit var appLauncher: AppLauncher
    private lateinit var appManager: AppManager
    private lateinit var appDrawerRecyclerView: RecyclerView
    private lateinit var appDrawerHintTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var launcherModeStatusTextView: TextView
    private lateinit var setLauncherButton: Button
    private lateinit var quietHoursSwitch: SwitchCompat
    private lateinit var quietHoursStatusTextView: TextView
    private lateinit var quietHoursStartButton: Button
    private lateinit var quietHoursEndButton: Button
    private lateinit var quietHoursResumeAppButton: Button
    private lateinit var quietHoursManager: QuietHoursManager
    private lateinit var appDrawerBottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var appDrawerAdapter: AppDrawerAdapter
    private var installedApps: List<AppInfo> = emptyList()

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 1001
        private const val TAG = "MainActivity"
        private const val PENDING_RESUME_RETRY_DELAY_MS = 400L
        private const val MAX_PENDING_RESUME_ATTEMPTS = 6
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
        quietHoursManager = QuietHoursManager(this)

        appDrawerRecyclerView = findViewById(R.id.app_drawer_recycler_view)
        appDrawerHintTextView = findViewById(R.id.app_drawer_hint_text_view)
        statusTextView = findViewById(R.id.status_text_view)
        launcherModeStatusTextView = findViewById(R.id.launcher_mode_status_text_view)
        setLauncherButton = findViewById(R.id.set_launcher_button)
        quietHoursSwitch = findViewById(R.id.quiet_hours_switch)
        quietHoursStatusTextView = findViewById(R.id.quiet_hours_status_text_view)
        quietHoursStartButton = findViewById(R.id.quiet_hours_start_button)
        quietHoursEndButton = findViewById(R.id.quiet_hours_end_button)
        quietHoursResumeAppButton = findViewById(R.id.quiet_hours_resume_app_button)
        val appDrawerBottomSheet: LinearLayout = findViewById(R.id.app_drawer_bottom_sheet)
        appDrawerBottomSheetBehavior = BottomSheetBehavior.from(appDrawerBottomSheet)

        setupAppDrawer()

        setLauncherButton.setOnClickListener {
            requestLauncherRole()
        }
        quietHoursStartButton.setOnClickListener {
            showTimePicker(isStartTime = true)
        }
        quietHoursEndButton.setOnClickListener {
            showTimePicker(isStartTime = false)
        }
        quietHoursResumeAppButton.setOnClickListener {
            showResumeAppPicker()
        }

        quietHoursSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            quietHoursManager.setEnabled(isChecked)
            if (isChecked) {
                quietHoursManager.scheduleAlarms()
            } else {
                quietHoursManager.cancelAlarms()
                QuietHoursActivity.stop(this)
            }
            refreshQuietHoursUi()
        }

        ensureNotificationPermissionAndStartService()
        updateLauncherModeStatus()
        refreshQuietHoursUi()
        loadAndDisplayApps()
    }

    private fun setupAppDrawer() {
        appDrawerAdapter = AppDrawerAdapter { selectedApp ->
            if (appLauncher.isAppInstalled(selectedApp.packageName)) {
                appLauncher.launchApp(selectedApp.packageName)
            }
        }

        appDrawerRecyclerView.layoutManager = LinearLayoutManager(this)
        appDrawerRecyclerView.adapter = appDrawerAdapter

        appDrawerBottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: android.view.View, newState: Int) {
                appDrawerHintTextView.text = if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    getString(R.string.app_drawer_open)
                } else {
                    getString(R.string.app_drawer_hint)
                }
            }

            override fun onSlide(bottomSheet: android.view.View, slideOffset: Float) {
                // No-op.
            }
        })
    }

    override fun onResume() {
        super.onResume()
        updateLauncherModeStatus()
        refreshQuietHoursUi()
        if (quietHoursManager.getSettings().enabled) {
            quietHoursManager.scheduleAlarms()
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        maybeLaunchPendingResumeApp()
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
                        openHomeLauncherSettings()
                        return
                    }
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    homeRoleLauncher.launch(intent)
                    return
                }
            }

            // Fallback for older Android versions and devices without role API support.
            openHomeLauncherSettings()
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open launcher settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateLauncherModeStatus() {
        val isDefaultLauncher = isDefaultHomeApp()
        val currentLauncherPackage = getCurrentHomePackage()

        if (isDefaultLauncher) {
            launcherModeStatusTextView.text = getString(
                R.string.launcher_mode_enabled_current,
                currentLauncherPackage ?: packageName
            )
            setLauncherButton.text = getString(R.string.manage_launcher_mode_button)
        } else {
            launcherModeStatusTextView.text = getString(
                R.string.launcher_mode_not_enabled_current,
                currentLauncherPackage ?: getString(R.string.unknown_launcher)
            )
            setLauncherButton.text = getString(R.string.enable_launcher_mode_button)
        }
    }

    private fun isDefaultHomeApp(): Boolean {
        val currentHomePackage = getCurrentHomePackage()
        return currentHomePackage == packageName
    }

    private fun getCurrentHomePackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    private fun openHomeLauncherSettings() {
        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    private fun loadAndDisplayApps() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.Default) {
                appManager.getInstalledApps()
            }
            installedApps = apps
            appDrawerAdapter.submitList(apps)

            refreshQuietHoursUi()
        }
    }

    private fun refreshQuietHoursUi() {
        val settings = quietHoursManager.getSettings()

        if (quietHoursSwitch.isChecked != settings.enabled) {
            quietHoursSwitch.isChecked = settings.enabled
        }

        if (settings.enabled) {
            val startText = formatTime(settings.startHour, settings.startMinute)
            val endText = formatTime(settings.endHour, settings.endMinute)
            quietHoursStatusTextView.text = getString(
                R.string.quiet_hours_status_format,
                startText,
                endText
            )
        } else {
            quietHoursStatusTextView.text = getString(R.string.quiet_hours_off)
        }

        quietHoursStartButton.text = getString(
            R.string.quiet_hours_start_time_set,
            formatTime(settings.startHour, settings.startMinute)
        )
        quietHoursEndButton.text = getString(
            R.string.quiet_hours_end_time_set,
            formatTime(settings.endHour, settings.endMinute)
        )

        val resumePackage = quietHoursManager.getResumeAppPackageName()
        val resumeName = installedApps.firstOrNull { it.packageName == resumePackage }?.name
        if (resumeName != null) {
            quietHoursResumeAppButton.text = getString(R.string.quiet_hours_resume_app_format, resumeName)
        } else {
            quietHoursResumeAppButton.text = getString(R.string.quiet_hours_resume_app_none)
        }
    }

    private fun showResumeAppPicker() {
        val pickerItems = mutableListOf(getString(R.string.quiet_hours_picker_none_option))
        pickerItems.addAll(installedApps.map { it.name })

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.quiet_hours_picker_title))
            .setItems(pickerItems.toTypedArray()) { _, which ->
                if (which == 0) {
                    quietHoursManager.setResumeAppPackageName(null)
                } else {
                    val app = installedApps[which - 1]
                    quietHoursManager.setResumeAppPackageName(app.packageName)
                }
                refreshQuietHoursUi()
            }
            .show()
    }

    private fun showTimePicker(isStartTime: Boolean) {
        val settings = quietHoursManager.getSettings()
        val initialHour = if (isStartTime) settings.startHour else settings.endHour
        val initialMinute = if (isStartTime) settings.startMinute else settings.endMinute

        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                if (isStartTime) {
                    quietHoursManager.setStartTime(hourOfDay, minute)
                } else {
                    quietHoursManager.setEndTime(hourOfDay, minute)
                }

                if (quietHoursManager.getSettings().enabled) {
                    quietHoursManager.scheduleAlarms()
                }
                refreshQuietHoursUi()
            },
            initialHour,
            initialMinute,
            android.text.format.DateFormat.is24HourFormat(this)
        ).show()
    }

    private fun maybeLaunchPendingResumeApp() {
        val resumePackage = quietHoursManager.getPendingResumeAppLaunch() ?: run {
            pendingResumeLaunchAttempts = 0
            pendingResumeRetryScheduled = false
            return
        }

        if (quietHoursManager.isNowInQuietHours()) {
            Log.i(TAG, "Skipping pending resume app launch because quiet hours are still active")
            return
        }

        val launched = appLauncher.launchApp(resumePackage)
        if (launched) {
            quietHoursManager.clearPendingResumeAppLaunch()
            pendingResumeLaunchAttempts = 0
            pendingResumeRetryScheduled = false
            Log.i(TAG, "Launched pending resume app: $resumePackage")
        } else {
            val lastError = appLauncher.getLastLaunchError()
            if (
                lastError == "BACKGROUND_ACTIVITY_START_BLOCKED" &&
                pendingResumeLaunchAttempts < MAX_PENDING_RESUME_ATTEMPTS
            ) {
                pendingResumeLaunchAttempts += 1
                if (!pendingResumeRetryScheduled) {
                    pendingResumeRetryScheduled = true
                    window.decorView.postDelayed({
                        pendingResumeRetryScheduled = false
                        maybeLaunchPendingResumeApp()
                    }, PENDING_RESUME_RETRY_DELAY_MS)
                }
                Log.i(
                    TAG,
                    "Retrying pending resume app launch: $resumePackage attempt=$pendingResumeLaunchAttempts"
                )
                return
            }

            quietHoursManager.clearPendingResumeAppLaunch()
            pendingResumeLaunchAttempts = 0
            pendingResumeRetryScheduled = false
            Log.w(TAG, "Failed to launch pending resume app: $resumePackage error=$lastError")
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.time)
    }
}
