package com.example.applauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.widget.Toast

class AppLauncher(private val context: Context) {

    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                context.startActivity(intent)
                true
            } else {
                showError("Could not find launch intent for $packageName")
                false
            }
        } catch (e: Exception) {
            showError("Error launching app: ${e.message}")
            false
        }
    }

    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
