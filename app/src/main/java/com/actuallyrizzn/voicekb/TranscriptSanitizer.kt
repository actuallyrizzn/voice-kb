package com.actuallyrizzn.voicekb

object TranscriptSanitizer {

    private const val SYSTEM_PROMPT = """
You fix speech-to-text output. Output ONLY the corrected plain text — no quotes, markdown, or commentary.

Rules:
- Fix homophones and obvious speech-recognition mistakes.
- Prefer spellings from the glossary when they fit context; do not invent new proper nouns.
- Do not change the user's meaning, add sentences, or omit content.
- Preserve punctuation and line breaks where reasonable.
- If the transcript is already correct, return it unchanged.
""".trimIndent()

    fun buildUserPrompt(contextTerms: String, rawTranscript: String): String = buildString {
        val ctx = contextTerms.trim()
        if (ctx.isNotEmpty()) {
            appendLine("Preferred terms and names (comma-separated):")
            appendLine(ctx)
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
