// BrowserPro Agent Bridge — Content Script
// Provides safe, limited DOM inspection and interaction for AI agent automation.
// Security: No password reading, no arbitrary JS, no full page text extraction.

(function() {
  "use strict";

  var agentIdCounter = 0;

  var INTERACTIVE_SELECTORS = [
    "a", "button", "input", "textarea", "select",
    '[role="button"]', '[contenteditable="true"]'
  ];

  function isVisible(el) {
    if (!el) return false;
    var rect = el.getBoundingClientRect();
    if (rect.width === 0 && rect.height === 0) return false;
    var style = window.getComputedStyle(el);
    return style.display !== "none" && style.visibility !== "hidden" && style.opacity !== "0";
  }

  function getElementText(el) {
    var text = (el.textContent || el.innerText || "").trim();
    return text.substring(0, 120).replace(/\s+/g, " ");
  }

  function buildDomSnapshot() {
    agentIdCounter = 0;
    var elements = [];
    var selector = INTERACTIVE_SELECTORS.join(",");
    var nodes = document.querySelectorAll(selector);
    var limit = Math.min(nodes.length, 150);

    for (var i = 0; i < limit; i++) {
      var el = nodes[i];
      agentIdCounter++;
      var id = agentIdCounter;
      el.setAttribute("data-agent-id", id);

      var rect = el.getBoundingClientRect();
      var tag = el.tagName.toLowerCase();
      var type = el.getAttribute("type") || "";

      var entry = {
        id: id,
        tag: tag,
        text: getElementText(el),
        ariaLabel: el.getAttribute("aria-label") || "",
        placeholder: el.getAttribute("placeholder") || "",
        type: type,
        href: tag === "a" ? (el.getAttribute("href") || "") : "",
        visible: isVisible(el),
        disabled: el.disabled || false,
        rect: {
          x: Math.round(rect.x),
          y: Math.round(rect.y),
          width: Math.round(rect.width),
          height: Math.round(rect.height)
        }
      };

      // Never return password values
      if (type === "password") {
        entry.text = "";
        entry.placeholder = el.getAttribute("placeholder") || "";
      }

      elements.push(entry);
    }

    return elements;
  }

  function findElementById(id) {
    return document.querySelector('[data-agent-id="' + id + '"]');
  }

  function handleGetDom() {
    return { ok: true, data: buildDomSnapshot() };
  }

  function handleClick(params) {
    if (!params || !params.id) return { ok: false, error: "Missing element id" };
    var el = findElementById(params.id);
    if (!el) return { ok: false, error: "Element not found" };
    if (!isVisible(el)) return { ok: false, error: "Element not visible" };
    if (el.disabled) return { ok: false, error: "Element is disabled" };
    el.click();
    return { ok: true, data: "clicked" };
  }

  function handleInputText(params) {
    if (!params || !params.id || typeof params.text !== "string") {
      return { ok: false, error: "Missing id or text" };
    }
    var el = findElementById(params.id);
    if (!el) return { ok: false, error: "Element not found" };
    var tag = el.tagName.toLowerCase();
    var type = (el.getAttribute("type") || "").toLowerCase();

    // Reject password fields
    if (type === "password") return { ok: false, error: "Cannot type into password fields" };

    // Only allow input, textarea, or contenteditable
    var isEditable = tag === "input" || tag === "textarea" || el.getAttribute("contenteditable") === "true";
    if (!isEditable) return { ok: false, error: "Element is not editable" };

    var text = params.text.substring(0, 1000);

    if (el.getAttribute("contenteditable") === "true") {
      el.textContent = text;
    } else {
      el.value = text;
    }
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
    return { ok: true, data: "text entered" };
  }

  function handleSubmitForm(params) {
    if (!params || !params.id) return { ok: false, error: "Missing element id" };
    var el = findElementById(params.id);
    if (!el) return { ok: false, error: "Element not found" };
    var form = el.closest("form");
    if (!form) return { ok: false, error: "No form found" };
    if (typeof form.requestSubmit === "function") {
      form.requestSubmit();
    } else {
      form.submit();
    }
    return { ok: true, data: "form submitted" };
  }

  function handleScroll(params) {
    if (!params || !params.direction) return { ok: false, error: "Missing direction" };
    var dir = params.direction.toLowerCase();
    if (dir !== "up" && dir !== "down") return { ok: false, error: "Direction must be up or down" };
    var amount = Math.round(window.innerHeight * 0.7);
    window.scrollBy(0, dir === "down" ? amount : -amount);
    return { ok: true, data: "scrolled " + dir };
  }

  function handleGetPageInfo() {
    return { ok: true, data: { title: document.title || "", url: window.location.href || "" } };
  }

  // Message listener
  browser.runtime.onMessage.addListener(function(message) {
    if (!message || !message.action) {
      return Promise.resolve({ ok: false, error: "Missing action" });
    }
    var action = message.action;
    var params = message.params || {};

    switch (action) {
      case "get_dom": return Promise.resolve(handleGetDom());
      case "click": return Promise.resolve(handleClick(params));
      case "input_text": return Promise.resolve(handleInputText(params));
      case "submit_form": return Promise.resolve(handleSubmitForm(params));
      case "scroll": return Promise.resolve(handleScroll(params));
      case "get_page_info": return Promise.resolve(handleGetPageInfo());
      default: return Promise.resolve({ ok: false, error: "Unknown action: " + action });
    }
  });
})();
