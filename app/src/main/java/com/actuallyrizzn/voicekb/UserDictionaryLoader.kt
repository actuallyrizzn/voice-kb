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

import android.content.ContentResolver
import android.provider.UserDictionary

/**
 * Reads the Android user dictionary ([UserDictionary]) — the same word list many keyboards
 * (including Gboard, when synced to system dictionary) contribute to. Gboard-only personal
 * data not stored here is not accessible.
 */
object UserDictionaryLoader {

    fun loadWords(contentResolver: ContentResolver, maxTerms: Int = 250): List<String> {
        val out = LinkedHashSet<String>()
        try {
            contentResolver.query(
                UserDictionary.Words.CONTENT_URI,
                arrayOf(UserDictionary.Words.WORD, UserDictionary.Words.FREQUENCY),
                null,
                null,
                "${UserDictionary.Words.FREQUENCY} DESC",
            )?.use { c ->
                val idx = c.getColumnIndex(UserDictionary.Words.WORD)
                if (idx < 0) return@use
                while (c.moveToNext() && out.size < maxTerms) {
                    val w = c.getString(idx)?.trim().orEmpty()
                    if (w.isNotEmpty()) out.add(w)
                }
            }
        } catch (_: SecurityException) {
            // READ_USER_DICTIONARY denied or provider unavailable
        }
        return out.toList()
    }
}
