const NATIVE_APP_ID = "browser";

const summarizeButton = document.getElementById("summarizeButton");
const loading = document.getElementById("loading");
const result = document.getElementById("result");
const error = document.getElementById("error");

summarizeButton.addEventListener("click", summarizePage);

async function summarizePage() {
  setBusy(true);
  showResult("");
  showError("");

  try {
    const tab = await getActiveTab();
    const pageText = await getPageText(tab.id);
    const response = await browser.runtime.sendNativeMessage(NATIVE_APP_ID, {
      type: "summarize",
      text: pageText
    });

    if (!response || response.ok !== true) {
      throw new Error(response && response.error ? response.error : "Native message failed.");
    }

    showResult(response.summary || "");
  } catch (exception) {
    showError(exception && exception.message ? exception.message : String(exception));
  } finally {
    setBusy(false);
  }
}

async function getActiveTab() {
  const tabs = await browser.tabs.query({
    active: true,
    currentWindow: true
  });

  if (!tabs || tabs.length === 0 || typeof tabs[0].id !== "number") {
    throw new Error("No active tab is available.");
  }

  return tabs[0];
}

async function getPageText(tabId) {
  let response;
  try {
    response = await browser.tabs.sendMessage(tabId, {
      type: "extractPageText"
    });
  } catch (exception) {
    throw new Error("Could not reach the page content script.");
  }

  if (!response || response.ok !== true) {
    throw new Error(response && response.error ? response.error : "Could not extract page text.");
  }

  const text = (response.text || "").trim();
  if (!text) {
    throw new Error("The page text is empty.");
  }

  return text;
}

function setBusy(isBusy) {
  summarizeButton.disabled = isBusy;
  loading.hidden = !isBusy;
}

function showResult(message) {
  result.textContent = message;
  result.hidden = !message;
}

function showError(message) {
  error.textContent = message;
  error.hidden = !message;
}
