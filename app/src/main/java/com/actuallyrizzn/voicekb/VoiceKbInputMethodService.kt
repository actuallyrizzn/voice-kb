package com.actuallyrizzn.voicekb

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.inputmethod.InputConnection
import androidx.core.content.ContextCompat
import com.actuallyrizzn.voicekb.databinding.ImeKeyboardBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceKbInputMethodService : InputMethodService(), RecognitionListener {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private lateinit var settings: SecureSettingsStore
    private var binding: ImeKeyboardBinding? = null
    private var speech: SpeechRecognizer? = null
    private var listening = false

    override fun onCreate() {
        super.onCreate()
        settings = SecureSettingsStore(this)
    }

    override fun onDestroy() {
        serviceJob.cancel()
        speech?.destroy()
        speech = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val b = ImeKeyboardBinding.inflate(layoutInflater)
        binding = b
        b.imeMic.setOnClickListener { onMicClicked() }
        return b.root
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopListeningInternal()
        super.onFinishInputView(finishingInput)
    }

    private fun onMicClicked() {
        val b = binding ?: return
        if (listening) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            b.imeStatus.setText(R.string.ime_need_mic_permission)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            b.imeStatus.setText(R.string.ime_error_recognition)
            return
        }
        startListeningInternal()
    }

    private fun startListeningInternal() {
        val b = binding ?: return
        listening = true
        b.imeStatus.setText(R.string.ime_listening)
        speech?.destroy()
        speech = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(this)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speech?.startListening(intent)
    }

    private fun stopListeningInternal() {
        listening = false
        try {
            speech?.stopListening()
            speech?.cancel()
        } catch (_: Exception) {
            // ignore
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        binding?.imeStatus?.setText(R.string.ime_processing)
    }

    override fun onError(error: Int) {
        listening = false
        val resId = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> R.string.ime_error_no_speech
            SpeechRecognizer.ERROR_CLIENT -> R.string.ime_error_recognition
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.ime_need_mic_permission
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            -> R.string.ime_error_recognition
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            -> R.string.ime_error_no_speech
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.ime_error_recognition
            else -> R.string.ime_error_recognition
        }
        binding?.imeStatus?.setText(resId)
    }

    override fun onResults(results: Bundle?) {
        listening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val raw = matches?.firstOrNull()
        binding?.imeStatus?.setText(R.string.ime_processing)
        if (raw.isNullOrBlank()) {
            binding?.imeStatus?.setText(R.string.ime_error_no_speech)
            return
        }
        val ic: InputConnection? = currentInputConnection
        serviceScope.launch {
            val result = withContext(Dispatchers.IO) { runPipeline(raw) }
            ic?.commitText(result.first, 1)
            binding?.imeStatus?.setText(result.second)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun runPipeline(raw: String): Pair<String, Int> {
        val key = settings.veniceApiKey().trim()
        val wantSanitize = settings.sanitizeEnabled() && key.isNotEmpty()
        if (!wantSanitize) return raw to R.string.ime_listen
        val model = settings.veniceModelId().trim()
        if (model.isEmpty()) return raw to R.string.ime_listen
        val base = settings.veniceBaseUrl()
        val terms = TermContextBuilder(this).buildContextString()
        val userPrompt = TranscriptSanitizer.buildUserPrompt(terms, raw)
        val maxTok = (raw.length * 2 + 128).coerceIn(256, 1024)
        return try {
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
