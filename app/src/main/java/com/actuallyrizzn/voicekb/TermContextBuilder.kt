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
