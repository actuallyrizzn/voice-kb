package com.actuallyrizzn.voicekb

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureSettingsStore(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        Prefs.FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun veniceApiKey(): String = prefs.getString(Prefs.VENICE_API_KEY, "").orEmpty()

    fun veniceModelId(): String = prefs.getString(Prefs.VENICE_MODEL_ID, "").orEmpty()

    fun sanitizeEnabled(): Boolean = prefs.getBoolean(Prefs.SANITIZE_ENABLED, true)

    fun veniceBaseUrl(): String =
        prefs.getString(Prefs.VENICE_BASE_URL, VeniceApi.DEFAULT_BASE_URL)?.trim().orEmpty()
            .ifEmpty { VeniceApi.DEFAULT_BASE_URL }
}
