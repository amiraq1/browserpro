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

## Summarization Architecture

The summarization logic is decoupled from `MainActivity` using a clean interface-based design:

```text
MainActivity (Native Message Delegate)
    │
    ▼
Summarizer (interface)
    │
    ├── MockSummarizer   ← currently active (first 100 chars)
    ├── RemoteApiSummarizer  ← future: OpenAI, Gemini, etc.
    └── LocalModelSummarizer ← future: on-device LLM
```

**Key components:**

- `Summarizer` — a suspend interface with a single `summarize(text): SummarizationResult` method.
- `SummarizationResult` — a sealed class with `Success(summary)` and `Error(message)` variants.
- `MockSummarizer` — returns the first 100 characters of the input text as a placeholder summary.
- `SummarizerFactory` — provides the active `Summarizer` implementation. Swap the implementation here to integrate a real AI service.

**Flow:**

1. `MainActivity` receives a native message from the WebExtension popup.
2. It extracts the page text from the JSON payload.
3. It calls `summarizer.summarize(text)` inside a coroutine.
4. The result is converted to a JSON response and completed on the `GeckoResult`.

**No real AI service is integrated yet.** The app still uses local mock summarization. Future implementations can replace `MockSummarizer` with a remote API summarizer, a local on-device model, or a hybrid approach — all without modifying `MainActivity` or the WebExtension.

## Real AI Summarization

The app supports real AI-powered summarization through a secure remote backend. The architecture ensures that **no AI provider API keys are stored in the Android app**.

**How it works:**

1. The Android app sends page text to a developer-owned backend endpoint.
2. The backend calls the actual AI provider (OpenAI, Gemini, etc.) using server-side credentials.
3. The backend returns the summary to the app.

**To enable remote summarization:**

1. Deploy a backend endpoint that implements the `/summarize` contract (see `docs/backend-example.md`).
2. Edit `AppConfig.kt`:
   - Set `SUMMARIZER_ENDPOINT` to your backend URL.
   - Set `USE_REMOTE_SUMMARIZER = true`.
3. Rebuild the app.

**Default behavior:** `MockSummarizer` is active (returns first 100 characters). No network calls are made unless remote mode is explicitly enabled.

> ⚠️ **Security Warning:** Never ship AI provider API keys (OpenAI, Gemini, etc.) inside the APK. Keys belong on the backend server only.

## Settings & Runtime Configuration

The app includes a Settings screen (⚙ button in the toolbar) that allows runtime configuration without rebuilding:

| Setting | Description | Default |
|---------|-------------|---------|
| Use Remote Summarizer | Toggle between mock and remote AI | Off |
| Backend URL | Your summarization endpoint | `https://example.com/summarize` |
| Summary Language | ISO 639-1 language code | `ar` |
| Max Summary Length | Maximum characters for the summary | `600` |

**How it works:**

- Settings are persisted in SharedPreferences and survive app restarts.
- When the user returns from Settings, the summarizer instance is refreshed automatically.
- URL validation enforces HTTPS (allows HTTP only for `localhost`, `127.0.0.1`, `10.0.2.2`).
- Invalid settings are rejected with clear error messages — the app never crashes.
- The browser continues working normally regardless of settings state.

**Reset:** Tap "Reset to Defaults" to restore all settings to their original values.

## Multi-Tab Browsing

The app supports multiple tabs, each backed by its own `GeckoSession`:

- **Tab count button** (shows number) — opens the tab switcher dialog.
- **+ button** — opens a new tab with Google.
- **Tab switcher** — lists all open tabs; tap to switch, ✕ to close.
- Closing the last tab automatically creates a fresh one.
- URL bar, progress indicator, and back navigation are synced to the active tab.
- The Σ summarizer works on whichever tab is currently active.

Architecture:

```text
MainActivity
    │
    ├── GeckoRuntime (single instance)
    ├── GeckoView (single instance, reused)
    └── TabManager
         ├── BrowserTab { id, session, url, title, state }
         ├── BrowserTab { ... }
         └── ...
```

Each tab has its own progress and navigation delegates. When switching tabs, the active session is detached from `GeckoView` and the new one is attached — preserving page state in background tabs.

## Bookmarks & History

Both features are accessible from the **⋮** menu button in the toolbar.

**Bookmarks:**
- Tap ⋮ → "★ Add Bookmark" to save the current page.
- Tap ⋮ → "☆ Remove Bookmark" if the page is already bookmarked.
- Tap ⋮ → "Bookmarks" to view all saved bookmarks.
- Tap a bookmark to open it in the active tab.
- Tap ✕ to delete a bookmark.
- Duplicates by URL are prevented automatically.

**History:**
- Browsing history is recorded automatically when a page finishes loading.
- Only `http://` and `https://` URLs are recorded (no `about:`, `resource:`, etc.).
- Consecutive visits to the same URL update the timestamp instead of creating duplicates.
- Tap ⋮ → "History" to view recent visits.
- Tap a history item to open it.
- Tap ✕ to delete individual items.
- Tap "Clear All" to wipe all history.
- History is capped at 500 items.

**Storage:** Both use SharedPreferences with JSON serialization. A future phase may migrate to Room for better performance with large datasets.

## Downloads Manager

Downloads are handled by Android's built-in `DownloadManager`:

- When GeckoView encounters a downloadable file, a confirmation dialog appears.
- Tap "Download" to start — the file is saved to the device's Downloads folder.
- Android system notifications show download progress and completion.
- Access downloads via ⋮ → "Downloads".
- Open completed files with the appropriate app.
- Remove download records from the list.

**How it works:**
1. `GeckoSession.ContentDelegate.onExternalResponse` detects download triggers.
2. The app extracts filename from `Content-Disposition` header or URL path.
3. `DownloadManager.Request` handles the actual download in the background.
4. A `BroadcastReceiver` updates the local record when the download completes.

**Storage:** Download records are persisted in SharedPreferences (JSON). The actual files live in the system Downloads folder.

## Appearance & Theme

The app supports three theme modes:

| Mode | Behavior |
|------|----------|
| Follow system | Matches the device's light/dark setting |
| Light | Always light theme |
| Dark | Always dark theme |

**How to change:** Settings (⋮ → Settings) → tap the Theme button → choose mode.

- Theme preference persists across app restarts.
- All screens (browser, settings, downloads) respect the selected theme.
- Material Design 3 color tokens (`colorSurface`, `colorOnSurface`, `colorError`, etc.) are used throughout.
- Dialogs (bookmarks, history, tabs, downloads) use theme-aware backgrounds and text colors.
- Delete/remove buttons use the theme's `colorError` for consistent styling in both modes.

## Ad Blocker & Tracker Protection

The app includes built-in privacy protection powered by GeckoView's Enhanced Tracking Protection:

| Protection | Default | Description |
|------------|---------|-------------|
| Block ads & trackers | On | Blocks advertising and content trackers |
| Tracker protection | On | Blocks social and known tracking domains |
| Block cryptominers | On | Blocks cryptocurrency mining scripts |
| Block fingerprinters | On | Blocks browser fingerprinting attempts |

**How to configure:** ⋮ → Settings → Privacy & Protection section → toggle switches → Save.

**Privacy Report:** ⋮ → "Privacy Protection" shows the current protection status and active site.

**Technical details:**
- Uses `ContentBlocking.AntiTracking` categories on `GeckoRuntimeSettings`.
- Settings are applied at runtime startup and refreshed when returning from Settings.
- Protection does not interfere with the summarizer extension or downloads.
- Future improvements may include per-site allowlists and custom filter lists.

## Next Steps

1. Tab session restore on app restart
2. Tab thumbnails
3. Private/incognito tabs
4. Bookmark folders and sync
5. History search
6. Downloads pause/resume
7. Per-site ad block allowlist
8. Custom filter lists
9. WebExtensions / XPI support
10. Extension management page
11. Privacy settings per site

## Find in Page

Available from the ⋮ menu → "Find in Page":

- A compact search bar appears below the toolbar.
- Type to search — matches are highlighted in real time.
- Use ↑/↓ buttons to navigate between matches.
- Match count (e.g., "3/12") is displayed.
- Tap ✕ to close the find bar and clear highlights.
- Find bar automatically closes when switching tabs or navigating to a new page.

Uses GeckoView's `GeckoSession.Finder` API (`session.finder.find()` / `session.finder.clear()`).

## Fullscreen & Immersive Browsing

**Web Fullscreen:**
- When a page requests fullscreen (e.g., video player), the browser toolbar hides and Android enters immersive sticky mode.
- Press Back to exit fullscreen before navigating back.
- Fullscreen exits safely when switching tabs or closing the active tab.
- System bars (status bar, navigation bar) are hidden during fullscreen.

**Immersive Browsing (toolbar toggle):**
- Tap ⋮ → "Hide toolbar" to hide the browser chrome for a cleaner reading experience.
- Tap ⋮ → "Show toolbar" to bring it back.
- The toolbar is always restored when exiting web fullscreen or switching tabs.

Uses `GeckoSession.ContentDelegate.onFullScreen(session, fullScreen)` for web fullscreen detection.

## Reader Mode

Available from the ⋮ menu → "Reader Mode":

- Extracts the main article content from the current page.
- Opens a clean, distraction-free reading screen.
- Adjustable text size (A- / A+) — preference persists across sessions.
- Supports light and dark themes.
- Works best on article-style pages (news, blogs, Wikipedia).

**How it works:**
1. A custom DOM event triggers the content script's article extraction logic.
2. The content script identifies the best content container (`<article>`, `<main>`, largest `<div>`).
3. Noise elements (nav, footer, ads, sidebars) are removed.
4. The extracted text is sent to Kotlin via native messaging.
5. ReaderActivity displays the clean content.

**Limitations:**
- Some complex single-page apps may not extract well.
- JavaScript-rendered content that hasn't loaded yet won't be captured.
- Extraction times out after 5 seconds if the content script doesn't respond.

**Future improvements:** Mozilla Readability algorithm, font selection, sepia theme, offline article saving.

## Share & Print

**Browser sharing (⋮ menu):**
- **Share Page** — opens Android share sheet with page title and URL.
- **Copy Link** — copies the current URL to clipboard.

**Reader Mode sharing:**
- **Share** — shares article title, URL, and a short excerpt.
- **Copy Link** — copies the article URL.
- **Print** — prints the article using Android Print Framework (renders to PDF with proper text wrapping and pagination).

**Notes:**
- Only `http://` and `https://` URLs can be shared or copied.
- Reader printing uses `PrintedPdfDocument` with Canvas text rendering — no WebView involved.
- No external services or tracking are used for sharing or printing.

## Modern Homepage

The native new tab homepage provides a clean browser start page without using Android WebView:

- A centered `نبض` title with a large Material search/address bar.
- Search input reuses the same URL/domain/search-engine logic as the main browser bar.
- Favorite Sites show default links for Google, YouTube, Wikipedia, and GitHub.
- Favorite Sites can be added, edited, removed, and opened from Material dialogs.
- Duplicate favorite URLs are rejected and invalid fields show inline errors.
- Recently Visited shows the latest history entries and opens them in the active tab.
- The homepage respects the selected search engine, tab state, and light/dark theme colors.

## Homepage & New Tab Page

New tabs display a native custom homepage with:

- **App title** (نبض) and search/address input.
- **Quick Links** — default sites (Google, YouTube, Wikipedia, GitHub) plus user-added links.
- **Recently Visited** — latest 5 history items for quick access.

**Behavior:**
- Tap + → custom homepage appears (no page loaded in GeckoView yet).
- Type a URL or search query → loads in the active tab, homepage hides.
- Tap a quick link or recent site → loads directly.
- When switching to a "home" tab, the homepage overlay shows.
- When switching to a normal tab, the homepage hides.

**Settings:** ⋮ → Settings → "Custom homepage for new tabs" toggle. When disabled, new tabs load Google directly.

**Storage:** Quick links are persisted in SharedPreferences (JSON). Recently visited uses the existing history repository.

## Clear Browsing Data

Available from ⋮ → "Clear Browsing Data":

| Category | Default Selected | Description |
|----------|-----------------|-------------|
| Browsing history | ✓ | Clears all visited page records |
| Cookies & site data | ✓ | Clears GeckoView cookies and site storage |
| Cached files | ✓ | Clears GeckoView network and image cache |
| Download records | ✗ | Removes download history (not the files) |
| Bookmarks | ✗ | Deletes all saved bookmarks |
| Quick links | ✗ | Resets homepage quick links to defaults |
| Settings | ✗ | Resets all app preferences to defaults |

**Clear on Exit:** Enable in Settings to automatically clear history, cookies, and cache when the app closes normally.

**Technical details:**
- Local data (history, bookmarks, downloads, quick links) uses SharedPreferences clearing.
- GeckoView data (cookies, cache) uses `StorageController.clearData()` with appropriate flags.
- A confirmation dialog prevents accidental data loss.
- Bookmarks and settings are never cleared by default — explicit selection required.

## File Tree

```text
.
├── .gitignore
├── README.md
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── docs
│   └── backend-example.md
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
            │               ├── MainActivity.kt
            │               ├── bookmarks
            │               │   ├── Bookmark.kt
            │               │   └── BookmarkRepository.kt
            │               ├── config
            │               │   └── AppConfig.kt
            │               ├── downloads
            │               │   ├── DownloadCompleteReceiver.kt
            │               │   ├── DownloadItem.kt
            │               │   ├── DownloadRepository.kt
            │               │   ├── DownloadStatusMapper.kt
            │               │   └── DownloadsActivity.kt
            │               ├── history
            │               │   ├── HistoryItem.kt
            │               │   └── HistoryRepository.kt
            │               ├── privacy
            │               │   └── PrivacyProtectionManager.kt
            │               ├── settings
            │               │   ├── SettingsActivity.kt
            │               │   └── SettingsRepository.kt
            │               ├── summarizer
            │               │   ├── MockSummarizer.kt
            │               │   ├── RemoteApiSummarizer.kt
            │               │   ├── SummarizationResult.kt
            │               │   ├── Summarizer.kt
            │               │   └── SummarizerFactory.kt
            │               ├── tabs
            │               │   ├── BrowserTab.kt
            │               │   └── TabManager.kt
            │               └── theme
            │                   └── ThemeManager.kt
            └── res
                ├── layout
                │   ├── activity_downloads.xml
                │   ├── activity_main.xml
                │   └── activity_settings.xml
                ├── values
                │   ├── colors.xml
                │   ├── strings.xml
                │   └── themes.xml
                └── values-night
                    └── themes.xml
```
