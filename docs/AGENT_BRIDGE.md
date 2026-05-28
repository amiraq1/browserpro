# BrowserPro Agent Bridge

## What is it?

A built-in WebExtension that provides a safe, limited bridge between the Android app and web pages. Designed for future local AI agent automation of the active browser tab.

## Security Model

**All capabilities default to OFF.** The user must explicitly enable each permission in Settings.

### Allowed Actions (strict allowlist)

| Action | Description | Required Permission |
|--------|-------------|-------------------|
| `get_dom` | Read simplified interactive DOM snapshot | allowDomRead |
| `get_page_info` | Read current page title and URL | allowDomRead |
| `click` | Click a visible, non-disabled element | allowClick |
| `input_text` | Type text into editable fields (not passwords) | allowTyping |
| `scroll` | Scroll page up or down | allowScroll |
| `submit_form` | Submit the nearest form | allowSubmit |

### Forbidden Capabilities (never implemented)

- ❌ Read cookies
- ❌ Read localStorage / sessionStorage
- ❌ Inject arbitrary JavaScript
- ❌ Execute eval() or run_js
- ❌ Use proxy APIs
- ❌ Intercept web requests (webRequest)
- ❌ Access browsing history
- ❌ Read password field values
- ❌ Extract full page body text
- ❌ Exfiltrate page content automatically

## JSON Protocol

### Request format
```json
{
  "action": "get_dom",
  "params": {}
}
```

### Response format (success)
```json
{
  "ok": true,
  "data": [...]
}
```

### Response format (error)
```json
{
  "ok": false,
  "error": "Action not permitted: click"
}
```

## Action Examples

### get_dom
```json
Request: { "action": "get_dom", "params": {} }
Response: { "ok": true, "data": [
  { "id": 1, "tag": "input", "type": "text", "placeholder": "Search", "visible": true, ... },
  { "id": 2, "tag": "button", "text": "Submit", "visible": true, ... }
]}
```

### click
```json
Request: { "action": "click", "params": { "id": 2 } }
Response: { "ok": true, "data": "clicked" }
```

### input_text
```json
Request: { "action": "input_text", "params": { "id": 1, "text": "hello world" } }
Response: { "ok": true, "data": "text entered" }
```

### scroll
```json
Request: { "action": "scroll", "params": { "direction": "down" } }
Response: { "ok": true, "data": "scrolled down" }
```

### submit_form
```json
Request: { "action": "submit_form", "params": { "id": 1 } }
Response: { "ok": true, "data": "form submitted" }
```

### get_page_info
```json
Request: { "action": "get_page_info", "params": {} }
Response: { "ok": true, "data": { "title": "Google", "url": "https://www.google.com/" } }
```

## GeckoView Native Messaging Limitation

GeckoView's native messaging model is **extension → Kotlin** (the extension calls `sendNativeMessage` or `connectNative`, and Kotlin responds via `MessageDelegate`).

**Kotlin cannot push commands TO the extension.** This means:
- The current implementation validates policy when the extension sends a message.
- For future app-initiated agent commands, a command queue pattern is needed where:
  1. Kotlin stores a pending command
  2. The extension polls for commands via periodic `sendNativeMessage` calls
  3. Or a content script event triggers the flow

This is documented as a TODO for future implementation.

## Manual Test Checklist

1. Open google.com
2. Enable Agent Bridge in Settings
3. Enable DOM Reading permission
4. Trigger `get_dom` → should return interactive elements
5. Enable Typing permission
6. `input_text` with search box id → text appears in search box
7. Enable Click permission
8. `click` on search button → search executes
9. Enable Scroll permission
10. `scroll` down → page scrolls
11. Disable bridge → all commands return "Agent bridge disabled"

## Known Limitations

- Only operates on the active tab
- No arbitrary JavaScript execution
- No page content extraction beyond interactive element snapshot
- Maximum 150 elements returned per DOM snapshot
- Text limited to 120 characters per element
- Input text limited to 1000 characters
- Native command dispatch from Kotlin to extension requires future command queue
- Password fields are never readable or writable
