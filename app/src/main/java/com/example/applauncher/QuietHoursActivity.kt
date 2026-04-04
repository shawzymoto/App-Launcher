package com.example.applauncher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class QuietHoursActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_CLOSE = "extra_close"

        fun start(context: Context) {
            val intent = Intent(context, QuietHoursActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }

        fun stop(context: Context) {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (handleCloseIntent(intent)) return

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        )

        setContentView(View(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
        })
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleCloseIntent(intent)
        }
    }

    private fun handleCloseIntent(intent: Intent): Boolean {
        if (intent.getBooleanExtra(EXTRA_CLOSE, false)) {
            finish()
            return true
        }
        return false
    }
}
