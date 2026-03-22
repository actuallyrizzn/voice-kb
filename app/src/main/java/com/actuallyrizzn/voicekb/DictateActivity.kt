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

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.actuallyrizzn.voicekb.databinding.ActivityDictateBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DictateActivity : AppCompatActivity(), RecognitionListener {

    private val activityJob = SupervisorJob()
    private val activityScope = CoroutineScope(activityJob + Dispatchers.Main.immediate)

    private lateinit var binding: ActivityDictateBinding
    private var speech: SpeechRecognizer? = null
    private var listening = false

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) {
            return@registerForActivityResult
        }
        binding.dictateStatus.setText(R.string.ime_need_mic_permission)
        openAppPermissionSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDictateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.dictateMic.setOnClickListener { onMicClicked() }
        binding.buttonEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.buttonOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (!VoiceKbAccessibilityService.isEnabled(this)) {
            binding.dictateStatus.setText(R.string.dictate_hint)
        }
    }

    override fun onDestroy() {
        activityJob.cancel()
        speech?.destroy()
        speech = null
        super.onDestroy()
    }

    override fun onPause() {
        stopListeningInternal()
        super.onPause()
    }

    private fun onMicClicked() {
        if (listening) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            binding.dictateStatus.setText(R.string.ime_need_mic_permission)
            openAppPermissionSettings()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            binding.dictateStatus.setText(R.string.ime_error_recognition)
            return
        }
        startListeningInternal()
    }

    private fun startListeningInternal() {
        listening = true
        binding.dictateStatus.setText(R.string.ime_listening)
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
        binding.dictateStatus.setText(R.string.ime_processing)
    }

    override fun onError(error: Int) {
        listening = false
        val status = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> R.string.ime_error_no_speech
            SpeechRecognizer.ERROR_CLIENT -> R.string.ime_error_recognition
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                openAppPermissionSettings()
                R.string.ime_need_mic_permission
            }
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER -> R.string.ime_error_recognition
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.ime_error_no_speech
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.ime_error_recognition
            else -> R.string.ime_error_recognition
        }
        binding.dictateStatus.setText(status)
    }

    override fun onResults(results: Bundle?) {
        listening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val raw = matches?.firstOrNull()
        binding.dictateStatus.setText(R.string.ime_processing)
        if (raw.isNullOrBlank()) {
            binding.dictateStatus.setText(R.string.ime_error_no_speech)
            return
        }

        activityScope.launch {
            val result = DictationPipeline.process(this@DictateActivity, raw)
            if (!VoiceKbAccessibilityService.insertTextIntoFocusedField(this@DictateActivity, result.first)) {
                copyToClipboard(result.first)
                binding.dictateStatus.setText(R.string.dictate_clipboard_fallback)
            } else if (result.second != R.string.ime_listen) {
                binding.dictateStatus.setText(result.second)
            } else {
                binding.dictateStatus.setText(R.string.ime_listen)
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Voice KB", text))
    }

    private fun openAppPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", packageName, null),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}
