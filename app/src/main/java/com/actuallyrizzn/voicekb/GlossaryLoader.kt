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
import java.io.File

/**
 * Loads glossary terms from [assets/default_glossary.txt] and optional [filesDir]/glossary.txt
 * (one term per line; `#` starts a comment).
 */
object GlossaryLoader {

    private const val ASSET_NAME = "default_glossary.txt"
    private const val USER_FILE = "glossary.txt"

    fun loadTerms(context: Context): List<String> {
        val out = LinkedHashSet<String>()
        loadAsset(context, out)
        val user = userGlossaryFile(context)
        if (user.isFile) {
            user.readLines().forEach { line ->
                val t = line.substringBefore('#').trim()
                if (t.isNotEmpty()) out.add(t)
            }
        }
        return out.toList()
    }

    fun loadUserTerms(context: Context): List<String> {
        val out = LinkedHashSet<String>()
        val user = userGlossaryFile(context)
        if (!user.isFile) return emptyList()
        user.readLines().forEach { line ->
            val t = line.substringBefore('#').trim()
            if (t.isNotEmpty()) out.add(t)
        }
        return out.toList()
    }

    fun saveUserTerms(context: Context, rawText: String) {
        val terms = mutableSetOf<String>()
        rawText.lineSequence().forEach { line ->
            val t = line.substringBefore('#').trim()
            if (t.isNotEmpty()) terms.add(t)
        }

        val user = userGlossaryFile(context)
        if (terms.isEmpty()) {
            if (user.isFile) {
                user.delete()
            }
            return
        }

        user.parentFile?.mkdirs()
        user.bufferedWriter().use { out ->
            terms.forEach { out.write(it); out.newLine() }
        }
    }

    private fun userGlossaryFile(context: Context): File = File(context.filesDir, USER_FILE)

    private fun loadAsset(context: Context, out: LinkedHashSet<String>) {
        try {
            context.assets.open(ASSET_NAME).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val t = line.substringBefore('#').trim()
                    if (t.isNotEmpty()) out.add(t)
                }
            }
        } catch (_: Exception) {
            // Missing asset is fine
        }
    }
}
