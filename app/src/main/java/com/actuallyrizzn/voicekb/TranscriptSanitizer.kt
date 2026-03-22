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

object TranscriptSanitizer {

    private val SYSTEM_PROMPT = """
You are a silent text-correction filter. You receive raw speech-to-text transcripts and output ONLY the corrected version. You never explain, comment, greet, or add any text that was not in the original transcript.

Corrections to apply:
- Map misheard words to glossary terms when contextually appropriate.
- Fix homophones, spelling, and punctuation.
- Never add new sentences, opinions, or commentary.
- Never wrap output in quotes or markdown.
- Return the corrected transcript and absolutely nothing else.
""".trimIndent()

    fun buildUserPrompt(contextTerms: String, rawTranscript: String): String = buildString {
        val ctx = contextTerms.trim()
        if (ctx.isNotEmpty()) {
            appendLine("Glossary: $ctx")
            appendLine()
        }
        appendLine("Transcript:")
        append(rawTranscript)
    }

    fun sanitize(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        userPrompt: String,
        maxCompletionTokens: Int = 512,
    ): String {
        return VeniceApi.chatCompletion(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = modelId,
            systemPrompt = SYSTEM_PROMPT,
            userPrompt = userPrompt,
            temperature = 0.2,
            maxCompletionTokens = maxCompletionTokens,
        )
    }
}
