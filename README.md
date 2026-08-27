# LumaRead 1.1

LumaRead is a local-first Android PDF eBook reader with hands-free Read Aloud for travel and screen-off listening.

## Core features

- Import PDF books with Android's document picker.
- Local bookshelf with covers, reading progress, and bookmarks.
- PDF page rendering with pinch-to-zoom and pan.
- Read Aloud from the current page and continue automatically across pages.
- Resume from the last spoken page and sentence.
- English (India) / Hindi detection and voice switching.
- Natural / Offline / System voice modes.
- Reading speed from 0.65x to 1.75x.
- Background and screen-off playback through an Android foreground media service.
- Notification controls for previous page, play/pause, next page, and stop.
- Audio-focus handling: pauses for calls/navigation/other audio and can resume after transient interruptions.
- Embedded PDF text extraction with PdfBox-Android.
- Offline OCR fallback for scanned pages using bundled ML Kit Latin + Devanagari models.
- No account, cloud upload, or server is required for books.

## Privacy

PDFs and extracted text remain on the device. The bundled OCR models work locally. `Natural` voice mode may select a network-backed voice if the installed Android TTS engine exposes one; choose `Offline` to restrict LumaRead to voices that do not require a network connection.

## Android requirements

- Minimum Android: 8.0 (API 26)
- compileSdk: 37
- targetSdk: 36
- Android Gradle Plugin: 9.3.0
- Gradle: 9.5.0
- Java/JDK: 17
- Jetpack Compose BOM: 2026.08.00

## Build the APK automatically on GitHub

The project includes `.github/workflows/build-apk.yml`.

1. Put the project files at the root of a GitHub repository.
2. Push to `main`, or open **Actions → Build LumaRead APK → Run workflow**.
3. After the workflow succeeds, open the workflow run.
4. Download the artifact named **LumaRead-APK**.
5. Extract it and install `LumaRead-debug.apk` on the Android device.

The debug APK is automatically signed for direct installation/testing.

## Build in Android Studio

Open this folder as an Android Studio project, use JDK 17 and Gradle 9.5.0, sync, then run `:app:assembleDebug`. The APK is written to:

`app/build/outputs/apk/debug/app-debug.apk`

## Libraries

- Android `PdfRenderer` for displaying pages.
- PdfBox-Android 2.0.27.0 for embedded PDF text extraction.
- ML Kit Text Recognition 16.0.1 for bundled Latin and Devanagari OCR.
- Android `TextToSpeech` for device-native speech synthesis.
