(function () {
  browser.runtime.onMessage.addListener((message) => {
    if (!message || message.type !== "extractPageText") {
      return false;
    }

    const body = document.body;
    if (!body) {
      return Promise.resolve({
        ok: false,
        text: "",
        error: "This page does not expose readable body text yet."
      });
    }

    const text = (body.innerText || "").trim();
    if (!text) {
      return Promise.resolve({
        ok: false,
        text: "",
        error: "No readable text was found on this page."
      });
    }

    return Promise.resolve({
      ok: true,
      text
    });
  });
})();
