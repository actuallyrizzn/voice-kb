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

import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent

/**
 * Shared [RecognizerIntent] for IME and DictateActivity. OEMs may ignore the
 * silence-length hints, but when honored they reduce premature end-of-speech.
 */
object SpeechListenIntent {

    /** Milliseconds of silence before the recognizer treats input as finished (API 29+). */
    private const val COMPLETE_SILENCE_MS = 3_500L

    /** Milliseconds of silence before input may be considered complete (API 29+). */
    private const val POSSIBLY_COMPLETE_SILENCE_MS = 2_500L

    fun create(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                COMPLETE_SILENCE_MS,
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                POSSIBLY_COMPLETE_SILENCE_MS,
            )
        }
    }
}
