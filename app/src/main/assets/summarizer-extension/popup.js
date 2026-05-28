// Popup script for Web Page Summarizer extension.
// Extracts page text via content script, then sends it to the Android app
// through browser.runtime.sendNativeMessage for summarization.

document.addEventListener("DOMContentLoaded", () => {
  const btn = document.getElementById("summarizeBtn");
  const status = document.getElementById("status");
  const result = document.getElementById("result");
  const error = document.getElementById("error");

  btn.addEventListener("click", async () => {
    // Clear previous output
    result.style.display = "none";
    result.textContent = "";
    error.style.display = "none";
    error.textContent = "";
    status.textContent = "Extracting page text...";

    try {
      // 1. Get the active tab
      const tabs = await browser.tabs.query({ active: true, currentWindow: true });
      if (!tabs || tabs.length === 0) {
        showError("No active tab found.");
        return;
      }
      const tab = tabs[0];

      // 2. Ask content script to extract page text
      let extraction;
      try {
        extraction = await browser.tabs.sendMessage(tab.id, { type: "extractText" });
      } catch (e) {
        showError("Could not reach content script. Try reloading the page.");
        return;
      }

      if (!extraction || !extraction.ok) {
        showError(extraction ? extraction.error : "Content script returned no data.");
        return;
      }

      const pageText = extraction.text || "";
      if (pageText.trim().length === 0) {
        showError("Page has no text content to summarize.");
        return;
      }

      // 3. Send text to Android native app for summarization
      status.textContent = "Summarizing...";
      let response;
      try {
        response = await browser.runtime.sendNativeMessage("browser", {
          type: "summarize",
          text: pageText
        });
      } catch (e) {
        showError("Native messaging failed: " + e.message);
        return;
      }

      // 4. Display result
      if (response && response.ok) {
        status.textContent = "";
        result.textContent = response.summary;
        result.style.display = "block";
      } else {
        showError(response ? response.error : "Invalid response from native app.");
      }
    } catch (e) {
      showError("Unexpected error: " + e.message);
    }
  });

  function showError(msg) {
    status.textContent = "";
    error.textContent = msg;
    error.style.display = "block";
  }
});
