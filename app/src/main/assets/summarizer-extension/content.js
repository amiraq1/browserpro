// Content script for Web Page Summarizer extension.
// Listens for messages from popup.js and native app, returns page text or article content.

browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message && message.type === "extractText") {
    try {
      if (!document || !document.body) {
        return Promise.resolve({ ok: false, error: "No document body available." });
      }
      const text = document.body.innerText || "";
      return Promise.resolve({ ok: true, text: text });
    } catch (e) {
      return Promise.resolve({ ok: false, error: "Failed to extract text: " + e.message });
    }
  }

  if (message && message.type === "extractArticle") {
    try {
      var result = extractArticle();
      // Send to native app for Reader Mode
      browser.runtime.sendNativeMessage("browser", {
        type: "readerArticle",
        title: result.ok ? result.title : "",
        byline: result.ok ? (result.byline || "") : "",
        content: result.ok ? result.content : "",
        ok: result.ok,
        error: result.ok ? "" : (result.error || "")
      });
      return Promise.resolve(result);
    } catch (e) {
      return Promise.resolve({ ok: false, error: "Article extraction failed: " + e.message });
    }
  }

  return false;
});

function extractArticle() {
  if (!document || !document.body) {
    return { ok: false, error: "No document body available." };
  }

  function getTextContent(el) {
    if (!el) return "";
    var clone = el.cloneNode(true);
    var remove = clone.querySelectorAll(
      "nav,footer,aside,script,style,noscript,header,form,button,iframe," +
      ".ad,.ads,.advertisement,.sidebar,.menu,.nav,.footer,.header"
    );
    for (var i = 0; i < remove.length; i++) { remove[i].remove(); }
    return (clone.innerText || clone.textContent || "").trim();
  }

  var title = document.title || "";
  var byline = "";
  var metaAuthor = document.querySelector('meta[name="author"]');
  if (metaAuthor) byline = metaAuthor.getAttribute("content") || "";

  var content = "";
  var article = document.querySelector("article");
  if (article) content = getTextContent(article);

  if (!content || content.length < 100) {
    var main = document.querySelector("main") || document.querySelector('[role="main"]');
    if (main) content = getTextContent(main);
  }

  if (!content || content.length < 100) {
    var candidates = document.querySelectorAll("div,section");
    var best = null, bestLen = 0;
    for (var i = 0; i < candidates.length && i < 50; i++) {
      var t = getTextContent(candidates[i]);
      if (t.length > bestLen) { bestLen = t.length; best = t; }
    }
    if (best && bestLen > 100) content = best;
  }

  if (!content || content.length < 100) {
    content = getTextContent(document.body);
  }

  if (!content || content.length < 50) {
    return { ok: false, error: "Not enough readable content on this page." };
  }

  return { ok: true, title: title, byline: byline, content: content };
}

// Listen for custom DOM event triggered from page context (via javascript: URI)
// This allows Kotlin to trigger article extraction without extension-internal messaging.
document.addEventListener("nabd-extract-article", function() {
  try {
    var result = extractArticle();
    browser.runtime.sendNativeMessage("browser", {
      type: "readerArticle",
      title: result.ok ? result.title : "",
      byline: result.ok ? (result.byline || "") : "",
      content: result.ok ? result.content : "",
      ok: result.ok,
      error: result.ok ? "" : (result.error || "")
    });
  } catch (e) {
    browser.runtime.sendNativeMessage("browser", {
      type: "readerArticle",
      ok: false,
      error: "Extraction failed: " + e.message,
      title: "",
      byline: "",
      content: ""
    });
  }
});
