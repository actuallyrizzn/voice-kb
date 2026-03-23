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

/**
 * If the model returns assistant-style content instead of a cleaned transcript,
 * fall back to the raw STT string. Mirrors [scripts/sanitizer_prompt_test.py].
 */
object SanitizerOutputGuard {

    fun shouldUseRawInstead(raw: String, sanitized: String): Boolean {
        val r = raw.trim()
        val o = sanitized.trim()
        if (o.isEmpty()) return true
        if (r.isEmpty()) return false

        if (o.length > r.length * 4 && r.length < 400) return true

        if (!r.contains('\n') && o.contains('\n') && o.length > (r.length * 1.2).toInt()) {
            return true
        }

        val rLower = r.lowercase()
        val oLower = o.lowercase()
        if ("why did the" in oLower && "why did the" !in rLower) {
            return true
        }
        return false
    }
}
