package com.vedesh.readfree.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsRepository(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getRaindropToken(): String? {
        return sharedPrefs.getString("raindrop_token", null)?.takeIf { it.isNotBlank() }
    }

    fun saveRaindropToken(token: String) {
        sharedPrefs.edit().putString("raindrop_token", token.trim()).apply()
    }

    fun isRaindropSyncEnabled(): Boolean {
        return sharedPrefs.getBoolean("raindrop_sync_enabled", false)
    }

    fun setRaindropSyncEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("raindrop_sync_enabled", enabled).apply()
    }
}
