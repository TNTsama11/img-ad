# ImgAd

[简体中文](README.md)

ImgAd is a local-first AI image client for Android 10 and later. It supports multiple OpenAI Images API-compatible providers and models, text-to-image generation, reference-image editing, local history, image preview, saving, sharing, and archive import/export.

## Features

- Configure multiple OpenAI-compatible providers and store API keys securely.
- Fetch provider models and select default providers and models.
- Enable generation, reference editing, masks, and multiple images according to model capabilities.
- Support long-running requests, cancellation, failure details, and retries.
- Browse session history, thumbnails, full-screen previews, and zoomable images.
- Save through Android MediaStore and share through the system chooser.
- Export and import local data; API keys are excluded by default.

## Screenshots

<p align="center">
  <img src="docs/images/create-session.png" width="30%" alt="Creation session" />
  <img src="docs/images/image-preview.png" width="30%" alt="Image preview" />
  <img src="docs/images/history.png" width="30%" alt="History" />
</p>

## Requirements

- Android 10 / API 29 or later
- JDK 17
- Android SDK 35

## Build and install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Configure a provider

1. Open Settings and select Add provider.
2. Enter a provider name, an OpenAI-compatible API base URL, and an API key.
3. Select Fetch models, choose the image models to import, and save.
4. Set the default provider and model, then return to Create.

Provider support for sizes, quality levels, editing, masks, and multiple images varies. Consult the provider documentation before use.

## Tests

```bash
./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
```

The project has been verified on an Android 15 / API 35 emulator. Physical-device testing should still cover Android 10, Android 14/15, the photo picker, MediaStore, sharing, and background recovery.

## Privacy and security

- API keys use Android Keystore-backed encrypted storage and are not stored in ordinary Room columns.
- Images, sessions, and configuration remain local by default.
- API keys are excluded from exports by default. Secret export requires explicit selection and password-based encryption.
- Generation requests are sent to the provider configured by the user. Review that provider's privacy policy and billing terms before use.

## Current limitations

- The repository does not provide a signed production APK.
- Accounts, cloud sync, and community features are out of scope.
- Compatibility depends on the provider implementing OpenAI-style image endpoints.
- No open-source license has been declared yet.
