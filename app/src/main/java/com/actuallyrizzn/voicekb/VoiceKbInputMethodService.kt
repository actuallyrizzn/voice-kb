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
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.actuallyrizzn.voicekb.databinding.ImeKeyboardBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
        b.imeSwitchKeyboard.setOnClickListener { switchToNextKeyboard() }
        b.imeBackspace.setOnClickListener { sendBackspace() }
        b.imeEnter.setOnClickListener { sendEnter() }
        b.imeSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
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
            openAppPermissionSettings()
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
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                openAppPermissionSettings()
                R.string.ime_need_mic_permission
            }
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
            val result = DictationPipeline.process(this@VoiceKbInputMethodService, raw)
            ic?.commitText(result.first, 1)
            binding?.imeStatus?.setText(result.second)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun sendBackspace() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (selected != null && selected.isNotEmpty()) {
            ic.commitText("", 1)
            return
        }
        if (!ic.deleteSurroundingText(1, 0)) {
            dispatchKeyDownUp(ic, KeyEvent.KEYCODE_DEL)
        }
    }

    private fun sendEnter() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        if (ei != null) {
            val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
            val noEnterAsAction = ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
            if (!noEnterAsAction &&
                action != EditorInfo.IME_ACTION_UNSPECIFIED &&
                action != EditorInfo.IME_ACTION_NONE
            ) {
                if (ic.performEditorAction(action)) return
            }
        }
        if (!ic.commitText("\n", 1)) {
            dispatchKeyDownUp(ic, KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun dispatchKeyDownUp(ic: InputConnection, keyCode: Int) {
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    @Suppress("DEPRECATION")
    private fun switchToNextKeyboard() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchToNextInputMethod(false)
            } else {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                val token = window?.window?.attributes?.token
                imm.switchToNextInputMethod(token, false)
            }
        } catch (_: Exception) {
            runCatching {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }
    }

    private fun openAppPermissionSettings() {
        val appPackage = packageName.takeIf { it.isNotBlank() } ?: return
        val uri = Uri.fromParts("package", appPackage, null)
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
    }
}
