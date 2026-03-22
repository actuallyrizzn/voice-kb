package com.actuallyrizzn.voicekb

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException

class SecureSettingsStore(context: Context) {

    private val appContext: Context = context.applicationContext
    val isEncryptedStore: Boolean
    val prefs: SharedPreferences

    init {
        var encryptedPrefs: SharedPreferences? = null
        try {
            val masterKey: MasterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            encryptedPrefs = EncryptedSharedPreferences.create(
                appContext,
                Prefs.FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: GeneralSecurityException) {
            // Fall back to normal prefs when Android keystore is unavailable.
        } catch (_: Exception) {
            // Fall back to normal prefs for any runtime issues around encryption setup.
        }

        prefs = encryptedPrefs ?: appContext.getSharedPreferences(
            Prefs.FILE,
            Context.MODE_PRIVATE,
        )
        isEncryptedStore = encryptedPrefs != null
    }

    fun veniceApiKey(): String = prefs.getString(Prefs.VENICE_API_KEY, "").orEmpty()

    fun veniceModelId(): String = prefs.getString(Prefs.VENICE_MODEL_ID, "").orEmpty()

    fun sanitizeEnabled(): Boolean = prefs.getBoolean(Prefs.SANITIZE_ENABLED, true)

    fun veniceBaseUrl(): String =
        prefs.getString(Prefs.VENICE_BASE_URL, VeniceApi.DEFAULT_BASE_URL)?.trim().orEmpty()
            .ifEmpty { VeniceApi.DEFAULT_BASE_URL }
}
