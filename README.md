# Voice KB

Samsung DeX–friendly **Android input method**: tap the mic, dictate with the device speech recognizer (which may use system cloud services depending on device settings), optionally run a **short Venice AI text pass** to clean up the transcript, then insert text at the cursor in whatever field already has focus.

## Architecture

1. **Local STT flow** — `SpeechRecognizer` / system recognizer for normal dictation, with optional Venice cleanup if enabled.
2. **Context** — Merges:
   - `app/src/main/assets/default_glossary.txt` (shipped; one term per line, `#` comments),
   - optional `filesDir/glossary.txt` on device (same format; add a future editor or push via adb),
   - words from Android **`UserDictionary`** (`READ_USER_DICTIONARY`) — the system dictionary many keyboards sync to. Gboard-specific data that never hits `UserDictionary` is **not** readable.
3. **Venice** — If cleanup is enabled and an API key is set, the app calls **`POST /chat/completions`** with a tight system prompt and the merged glossary block. Model list comes from **`GET /models`** (no API key required), sorted by a simple **pricing index** from `model_spec.pricing` (`input.usd` + `output.usd` when present). Same HTTP surface as the Python SDK in [`../venice-ai-sdk`](../venice-ai-sdk) (`venice_sdk/endpoints.py`, `venice_sdk/chat.py`, `venice_sdk/models.py`).

Default API base: `https://api.venice.ai/api/v1`.

## Setup

1. Open the project in Android Studio (or set `sdk.dir` in `local.properties`; see `local.properties.example`).
2. Build/run **app** on device or emulator with a microphone.
3. Open **Voice KB** from the launcher → allow **microphone**.
4. Tap **Refresh model list** (uses public `GET /models`), pick a **cheap text model**, enter your **Venice API key**, toggle **Clean up transcript**, **Save**.
5. System settings → enable **Voice KB** as an on-screen keyboard, then switch to it when dictating in any app.

## Privacy

- **Speech audio** goes to the **system speech service** (same as other apps using `SpeechRecognizer`).
- If cleanup is on, **the text transcript** plus **glossary / user-dictionary terms** are sent to **Venice**.

## Reliability

- If Android secure storage is unavailable, settings are stored in a regular SharedPreferences file with a warning. This is a safer fallback than crashing the IME but still stores the API key on disk.

## Requirements

- `RECORD_AUDIO`, `READ_USER_DICTIONARY`, `INTERNET`
- Min SDK 26
