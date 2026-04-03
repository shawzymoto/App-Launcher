package com.example.applauncher

import android.content.Context
import android.content.SharedPreferences

class ApiKeyManager(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("api_config", Context.MODE_PRIVATE)

    companion object {
        private const val API_KEY_PREF = "api_key"
        private const val API_KEY_DEFAULT = "app-launcher-default-key"
    }

    fun getApiKey(): String {
        return sharedPreferences.getString(API_KEY_PREF, API_KEY_DEFAULT) ?: API_KEY_DEFAULT
    }

    fun setApiKey(key: String) {
        sharedPreferences.edit().putString(API_KEY_PREF, key).apply()
    }

    fun verifyApiKey(providedKey: String): Boolean {
        return providedKey == getApiKey()
    }

    fun resetApiKey() {
        setApiKey(API_KEY_DEFAULT)
    }
}
