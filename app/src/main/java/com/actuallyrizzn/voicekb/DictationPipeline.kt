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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DictationPipeline {
    suspend fun process(context: Context, raw: String): Pair<String, Int> = withContext(Dispatchers.IO) {
        val settings = SecureSettingsStore(context)
        val key = settings.veniceApiKey().trim()
        val wantSanitize = settings.sanitizeEnabled() && key.isNotEmpty()

        if (!wantSanitize) {
            return@withContext raw to R.string.ime_listen
        }

        val model = settings.veniceModelId().trim()
        if (model.isEmpty()) {
            return@withContext raw to R.string.ime_listen
        }

        val base = settings.veniceBaseUrl()
        val terms = TermContextBuilder(context).buildContextString()
        val userPrompt = TranscriptSanitizer.buildUserPrompt(terms, raw)
        val maxTok = (raw.length * 2 + 128).coerceIn(256, 1024)

        return@withContext try {
            val sanitized = TranscriptSanitizer.sanitize(
                baseUrl = base,
                apiKey = key,
                modelId = model,
                userPrompt = userPrompt,
                maxCompletionTokens = maxTok,
            )
            sanitized.ifBlank { raw } to R.string.ime_listen
        } catch (_: Exception) {
            raw to R.string.ime_status_sanitize_fallback
        }
    }
}
