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
        val user = File(context.filesDir, USER_FILE)
        if (user.isFile) {
            user.readLines().forEach { line ->
                val t = line.substringBefore('#').trim()
                if (t.isNotEmpty()) out.add(t)
            }
        }
        return out.toList()
    }

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
