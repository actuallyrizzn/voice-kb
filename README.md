# Voice KB

**Code:** AGPL-3.0 — see [LICENSE](LICENSE).
**Non-code content (docs, README, assets):** CC BY-SA 4.0 — see [LICENSE-DOCS](LICENSE-DOCS).

Samsung DeX-friendly Android dictation app. Tap the mic, speak, and the transcribed text is inserted into whatever field last had focus — even when a Bluetooth keyboard is connected and the on-screen keyboard is hidden.

Optionally runs a Venice AI text pass to clean up the raw transcript using a custom glossary of proper nouns and domain terms.

## How it works

```
 ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
 │  Tap mic in  │────▶│   Android    │────▶│  Raw text     │
 │ DictateActivity│    │SpeechRecognizer│   │  transcript   │
 └──────────────┘     └──────────────┘     └──────┬───────┘
                                                   │
                                    ┌──────────────▼───────────────┐
                                    │  Sanitize enabled + API key? │
                                    └──────┬───────────┬───────────┘
                                      yes  │           │  no
                                ┌──────────▼──────┐    │
                                │  Venice AI      │    │
                                │  chat/completions│   │
                                │  + glossary ctx │    │
                                └──────────┬──────┘    │
                                           │           │
                                    ┌──────▼───────────▼──┐
                                    │  Insert into focused │
                                    │  field (accessibility│
                                    │  service or clipboard│
                                    │  fallback)           │
                                    └──────────────────────┘
```

### Components

| Component | Purpose |
|---|---|
| `DictateActivity` | Launcher activity with a mic button. Listens via `SpeechRecognizer`, runs the pipeline, inserts text via the accessibility service. |
| `SettingsActivity` | Configure Venice API key, model selection, sanitization toggle, and custom glossary terms. |
| `VoiceKbInputMethodService` | Optional IME path for use as an on-screen keyboard (secondary to the launcher activity). |
| `VoiceKbAccessibilityService` | Injects text into the previously focused editable field from any app. Falls back to clipboard if unavailable. |
| `DictationPipeline` | Shared coroutine pipeline: raw STT → optional Venice sanitization → result. |
| `TranscriptSanitizer` | Builds the Venice prompt with glossary context and calls `chat/completions`. |
| `VeniceApi` | Thin HTTP client for Venice AI (`GET /models`, `POST /chat/completions`). |
| `GlossaryLoader` | Loads terms from shipped defaults (`assets/default_glossary.txt`) and user-maintained list (`filesDir/glossary.txt`). |
| `UserDictionaryLoader` | Reads the Android system `UserDictionary` for additional context terms. |
| `TermContextBuilder` | Merges glossary and system dictionary terms into a single context string for the sanitizer prompt. |
| `SecureSettingsStore` | Encrypted `SharedPreferences` with automatic fallback to standard storage. |

### Text insertion

The app uses an `AccessibilityService` to find the currently focused editable field and insert text via `ACTION_SET_TEXT` or `ACTION_PASTE`. This works even when a hardware keyboard is connected and no on-screen keyboard is active.

If the accessibility service is not enabled or can't find an editable field, the text is copied to the clipboard with a status message.

### Venice AI sanitization

When enabled, the raw transcript is sent to Venice AI along with a glossary of preferred terms. The system prompt instructs the model to act as a silent text-correction filter: it fixes homophones, maps misheard words to glossary entries, and returns only the corrected text with no commentary or wrapper text.

The glossary is built from three sources, merged at runtime:
1. `assets/default_glossary.txt` — shipped defaults (one term per line, `#` comments).
2. `filesDir/glossary.txt` — user-maintained via the Settings screen.
3. Android `UserDictionary` — system-level words from keyboards that sync to it.

Model list is fetched from `GET /models` (no API key required) and sorted by pricing index.

## Setup

1. Install the APK on a device running Android 8.0+ (API 26).
2. Open **Voice KB** from the launcher.
3. Grant microphone permission when prompted.
4. Tap **Enable accessibility insertion** and enable Voice KB in Android Accessibility settings.
5. Tap **Voice KB settings** to configure:
   - Enter your **Venice API key** (optional; only needed for transcript cleanup).
   - Tap **Refresh model list** and pick a text model.
   - Toggle **Clean up transcript with Venice**.
   - Add custom terms under **Custom terms for context** (one per line).
   - Tap **Save**.

### Building from source

```bash
git clone https://github.com/actuallyrizzn/voice-kb.git
cd voice-kb
# Set sdk.dir in local.properties (see local.properties.example)
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Microphone access for speech recognition. |
| `READ_USER_DICTIONARY` | Read system dictionary terms for sanitization context. |
| `INTERNET` | Venice AI API calls (model list and transcript cleanup). |

## Privacy

- **Speech audio** goes to the system speech service (`SpeechRecognizer`), which may use cloud processing depending on device settings.
- If cleanup is enabled, **the text transcript** and **glossary terms** are sent to Venice AI. No audio is sent to Venice.
- API keys are stored in `EncryptedSharedPreferences` where available, with automatic fallback to standard `SharedPreferences` (with a warning).

## Requirements

- Android 8.0+ (API 26)
- `compileSdk 34`
- Kotlin 2.0, AGP 8.7

## License

**Code** is licensed under the [GNU Affero General Public License v3.0](LICENSE).

**Non-code content** (documentation, README, asset files) is licensed under [Creative Commons Attribution-ShareAlike 4.0 International](LICENSE-DOCS).
