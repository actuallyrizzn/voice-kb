/*
 * Voice KB — Android dictation with Venice AI cleanup
 * Copyright (C) 2026 actuallyrizzn
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
