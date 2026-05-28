# نبض

نبض is a small Android web browser built with Kotlin, XML layouts, Material Design 3, and Mozilla GeckoView. It opens web pages with GeckoView, provides a compact URL/search bar, remembers the last opened URL, and includes a bundled WebExtension named **Web Page Summarizer**.

## Why GeckoView

This app intentionally does not use Android WebView. GeckoView bundles Mozilla's browser engine directly into the app, which gives the project a Firefox-compatible rendering engine and WebExtension support without depending on the device WebView provider.

## Features

- Opens web pages using Mozilla GeckoView.
- Loads `https://www.google.com` by default.
- Restores the last opened URL from SharedPreferences.
- Supports direct URLs, domain input, and Google search fallback.
- Shows basic page loading progress.
- Supports Android back navigation through GeckoSession history.
- Provides a compact `Σ` action button that opens the bundled extension popup.
- Installs a built-in Manifest V2 WebExtension from `resource://android/assets/summarizer-extension/`.
- Uses GeckoView native messaging from `popup.js` to Kotlin.
- Returns a local mock summary from Kotlin.

## Build In Android Studio

1. Open this folder in Android Studio.
2. Let Gradle sync the project.
3. Make sure Android SDK Platform 35 is installed.
4. Select the `app` run configuration.
5. Run on an Android device or emulator.

The project uses:

- Android Gradle Plugin `8.7.3`
- Kotlin Android plugin `1.9.23`
- GeckoView `126.0.20240526221752`
- Material Components `1.14.0`

Mozilla's Maven repository is configured in `settings.gradle.kts`:

```kotlin
maven("https://maven.mozilla.org/maven2/")
```

## Built-In WebExtension

The extension lives in:

```text
app/src/main/assets/summarizer-extension/
```

It is installed by `MainActivity.installSummarizerExtension()` with:

```kotlin
runtime.webExtensionController.ensureBuiltIn(
    EXTENSION_LOCATION,
    EXTENSION_ID
)
```

The extension id is `summarizer@example.com`, matching `browser_specific_settings.gecko.id` in `manifest.json`.

## Native Messaging Flow

1. `popup.js` finds the active tab.
2. `popup.js` asks `content.js` for `document.body.innerText`.
3. `popup.js` sends the extracted text to Android:

```javascript
browser.runtime.sendNativeMessage("browser", {
  type: "summarize",
  text: pageText
});
```

4. Kotlin registers `WebExtension.MessageDelegate` with the same native app id: `browser`.
5. Kotlin validates the JSON payload and returns:

```json
{
  "ok": true,
  "summary": "..."
}
```

Errors are returned as:

```json
{
  "ok": false,
  "error": "error message"
}
```

## Current Limitation

Summarization is mocked locally in Kotlin. The app returns the first 100 characters of the extracted page text followed by `...`.

## Next Steps

1. Real summarization model or API
2. Tabs
3. Bookmarks
4. History
5. Downloads
6. Dark mode polishing
7. Ad blocker
8. WebExtensions / XPI support
9. Extension management page
10. Privacy settings

## File Tree

```text
.
├── .gitignore
├── README.md
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── app
    ├── build.gradle.kts
    └── src
        └── main
            ├── AndroidManifest.xml
            ├── assets
            │   └── summarizer-extension
            │       ├── content.js
            │       ├── manifest.json
            │       ├── popup.css
            │       ├── popup.html
            │       └── popup.js
            ├── java
            │   └── com
            │       └── amiraq
            │           └── nabd
            │               └── MainActivity.kt
            └── res
                ├── layout
                │   └── activity_main.xml
                ├── values
                │   ├── colors.xml
                │   ├── strings.xml
                │   └── themes.xml
                └── values-night
                    └── themes.xml
```
