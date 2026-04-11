package com.example.applauncher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class QuietHoursActivity : AppCompatActivity() {

    private val quietHoursManager by lazy { QuietHoursManager(this) }

    companion object {
        const val EXTRA_ALLOW_ONE_QUIET_RESUME = "extra_allow_one_quiet_resume"
        private const val EXTRA_CLOSE = "extra_close"
        private const val FALLBACK_TIMEOUT_MS = 30_000L
        private const val TIMEOUT_MARGIN_MS = 1_000L
        @Volatile
        private var isOverlayVisible = false

        fun start(context: Context) {
            val intent = Intent(context, QuietHoursActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
            context.startActivity(intent)
        }

        fun stop(context: Context) {
            if (!isOverlayVisible) return
            val intent = Intent(context, QuietHoursActivity::class.java).apply {
                putExtra(EXTRA_CLOSE, true)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
            context.startActivity(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        isOverlayVisible = true
    }

    override fun onStop() {
        super.onStop()
        isOverlayVisible = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (handleCloseIntent(intent)) return
        if (!shouldAllowQuietOverlay()) {
            finish()
            return
        }

        applyQuietWindowFlags()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(false)
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        )

        val rootView = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
        }

        rootView.setOnClickListener {
            if (shouldAllowQuietOverlay()) {
                unlockTemporarilyAndOpenLauncher()
            }
        }

        setContentView(rootView)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleCloseIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!shouldAllowQuietOverlay()) {
            finish()
            return
        }

        applyQuietWindowFlags()
    }

    private fun handleCloseIntent(intent: Intent): Boolean {
        if (intent.getBooleanExtra(EXTRA_CLOSE, false)) {
            finish()
            return true
        }
        return false
    }

    private fun shouldAllowQuietOverlay(): Boolean {
        val settings = quietHoursManager.getSettings()
        return settings.enabled && quietHoursManager.isNowInQuietHours(settings)
    }

    private fun applyQuietWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }

        if (quietHoursManager.isPreventScreenLockEnabled()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
        }
    }

    private fun unlockTemporarilyAndOpenLauncher() {
        quietHoursManager.beginTemporaryWake(getSystemScreenTimeoutMs())

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_ALLOW_ONE_QUIET_RESUME, true)
        }
        startActivity(launchIntent)
        finish()
    }

    private fun getSystemScreenTimeoutMs(): Long {
        return try {
            val configuredTimeout = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT
            ).toLong()
            (configuredTimeout - TIMEOUT_MARGIN_MS).coerceAtLeast(1_000L)
        } catch (_: Exception) {
            (FALLBACK_TIMEOUT_MS - TIMEOUT_MARGIN_MS).coerceAtLeast(1_000L)
        }
    }

}
