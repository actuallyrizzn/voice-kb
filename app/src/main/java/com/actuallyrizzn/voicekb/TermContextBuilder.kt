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

/**
 * Merges local glossary + user dictionary into one comma-separated block for the Venice prompt.
 * Capped to avoid blowing token limits.
 */
class TermContextBuilder(context: Context) {

    private val app = context.applicationContext

    fun buildContextString(maxChars: Int = 3500): String {
        val terms = LinkedHashSet<String>()
        GlossaryLoader.loadTerms(app).forEach { terms.add(it) }
        UserDictionaryLoader.loadWords(app.contentResolver).forEach { terms.add(it) }
        val joined = terms.joinToString(", ")
        return if (joined.length <= maxChars) joined else joined.take(maxChars)
    }
}
