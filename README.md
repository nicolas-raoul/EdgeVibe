# EdgeVibe

EdgeVibe is an experimental, on-device AI Android application designed to generate, preview, and save single-file HTML/JS web applications. It leverages the power of local foundation models to instantly convert natural language descriptions into interactive web experiences—entirely on your device, with no internet connection required.

## 🚀 Features

* **Instant Webapp Generation:** Describe your app in natural language (e.g., "A random addition quiz with a score counter") and get a functional, single-file HTML/JS application within seconds.
* **On-Device AI Backends:** Choose between three different local AI engines to power your generation:
    * **MLKit (Gemini Nano):** Google's task-oriented API for Gemini Nano.
    * **AI Edge SDK (Gemini Nano):** Direct, low-level access to Gemini Nano with an expanded context window (up to 8192 output tokens) to prevent truncation of complex apps.
    * **Qwen 3.5 2B (MediaPipe):** An alternative 2B parameter model running via MediaPipe Tasks GenAI.
* **Interactive Preview & Inspection:**
    * **App Tab:** Instantly use your generated webapp in a fully functional `WebView`.
    * **Prompt Tab:** Review the original description used for generation.
    * **HTML Tab:** Inspect the raw, unescaped HTML/JS source code.
    * **Errors Tab:** A built-in JavaScript console logger that catches and displays any runtime errors within your generated app, complete with a notification badge.
* **Save & Open:** Save generated webapps to your device storage. Gemini Nano automatically suggests a concise, relevant name based on your prompt. Reopen your saved webapps instantly without regenerating.

## 🛠️ Technical Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material 3)
* **AI Integration:** 
    * `com.google.mlkit:genai-prompt`
    * `com.google.ai.edge.aicore:aicore`
    * `com.google.mediapipe:tasks-genai`
* **Architecture:** Modern Android development practices following the "Now in Android" structure.
* **Minimum SDK:** API 31 (Required for AI Edge SDK compatibility)

## ⚙️ Setup & Configuration

### Prerequisites
* Android Studio (latest stable version recommended)
* A physical Android device with Gemini Nano support (e.g., Pixel 8 Pro, Galaxy S24 series) or a compatible emulator.

### Qwen 3.5 2B Configuration (Optional)
To use the Qwen 3.5 2B backend via MediaPipe, you must manually provide the model file:
1. Download a MediaPipe-compatible Qwen 3.5 2B `.bin` file.
2. Place the file in the app's external files directory on your device:
   `/Android/data/io.github.nicolasraoul.edgevibe/files/qwen.bin`
3. Launch the app, click the Settings (⚙️) icon, and select "Qwen 3.5 2B (MediaPipe)".

## 🏗️ Building and Running

You can build and install the app directly from your terminal using Gradle and ADB:

```bash
# Build the debug APK
gradle assembleDebug

# Install on a connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell monkey -p io.github.nicolasraoul.edgevibe -c android.intent.category.LAUNCHER 1
```

## 📝 User Manual

For detailed instructions on how to use EdgeVibe, including simulation examples, please see the [User Manual](USER_MANUAL.md) located in the `testing/` directory.

## 🔒 Privacy

EdgeVibe is designed with privacy in mind. All AI generation, processing, and file storage happen locally on your device. Your prompts and generated code are never sent to a cloud server.
