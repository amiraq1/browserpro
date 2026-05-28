# Backend Summarization Endpoint — Example

This document describes the expected behavior of the backend endpoint that the نبض browser app calls for AI-powered summarization.

## Why a Backend?

The Android app does **not** store any AI provider API keys. All secret credentials live on the backend server. This prevents key extraction from the APK and allows key rotation without app updates.

## Endpoint Contract

### Request

```
POST /summarize
Content-Type: application/json; charset=utf-8
```

```json
{
  "text": "The full page text extracted from the browser...",
  "language": "ar",
  "maxLength": 600
}
```

| Field       | Type   | Description                                      |
|-------------|--------|--------------------------------------------------|
| `text`      | string | Page text to summarize (max ~12,000 characters)  |
| `language`  | string | Preferred summary language (ISO 639-1 code)      |
| `maxLength` | int    | Maximum desired summary length in characters     |

### Response — Success

```json
{
  "ok": true,
  "summary": "ملخص الصفحة هنا..."
}
```

### Response — Error

```json
{
  "ok": false,
  "error": "Description of what went wrong"
}
```

## Example Backend (Node.js + Express + OpenAI)

```javascript
const express = require("express");
const OpenAI = require("openai");

const app = express();
app.use(express.json({ limit: "50kb" }));

const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY // Key lives in environment, NOT in app
});

app.post("/summarize", async (req, res) => {
  const { text, language, maxLength } = req.body;

  if (!text || text.trim().length === 0) {
    return res.json({ ok: false, error: "No text provided" });
  }

  try {
    const response = await openai.responses.create({
      model: "gpt-4o-mini",
      input: [
        {
          role: "system",
          content: `Summarize the following text in ${language || "ar"}. Keep the summary under ${maxLength || 600} characters.`
        },
        {
          role: "user",
          content: text.substring(0, 12000)
        }
      ]
    });

    const summary = response.output_text || "";
    res.json({ ok: true, summary });
  } catch (err) {
    console.error("Summarization error:", err.message);
    res.json({ ok: false, error: "AI summarization failed" });
  }
});

app.listen(3000, () => console.log("Summarizer backend running on :3000"));
```

## Example Backend (Python + Flask + Gemini)

```python
import os
from flask import Flask, request, jsonify
import google.generativeai as genai

app = Flask(__name__)
genai.configure(api_key=os.environ["GEMINI_API_KEY"])
model = genai.GenerativeModel("gemini-1.5-flash")

@app.route("/summarize", methods=["POST"])
def summarize():
    data = request.get_json()
    text = (data.get("text") or "").strip()
    language = data.get("language", "ar")
    max_length = data.get("maxLength", 600)

    if not text:
        return jsonify({"ok": False, "error": "No text provided"})

    try:
        prompt = (
            f"Summarize the following text in {language}. "
            f"Keep the summary under {max_length} characters.\n\n"
            f"{text[:12000]}"
        )
        response = model.generate_content(prompt)
        summary = response.text.strip()
        return jsonify({"ok": True, "summary": summary})
    except Exception as e:
        return jsonify({"ok": False, "error": "AI summarization failed"})

if __name__ == "__main__":
    app.run(port=3000)
```

## Security Reminders

- **Never** put `OPENAI_API_KEY`, `GEMINI_API_KEY`, or any provider key in the Android app.
- Use environment variables or a secrets manager on the backend.
- Consider adding rate limiting and authentication to the endpoint.
- Use HTTPS only.
- Validate and sanitize input text on the backend.
