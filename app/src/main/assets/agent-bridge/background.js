// BrowserPro Agent Bridge — Background Script
// Polls Kotlin for pending commands and routes them to content script.
// Security: Only allowlisted actions are forwarded.

(function() {
  "use strict";

  var NATIVE_APP_ID = "browserpro_agent";
  var POLL_INTERVAL_MS = 1000;
  var POLL_INTERVAL_DISABLED_MS = 3000;
  var ALLOWED_ACTIONS = ["get_dom", "click", "input_text", "submit_form", "scroll", "get_page_info"];

  var pollTimer = null;

  function startPolling() {
    if (pollTimer) return;
    poll();
  }

  function poll() {
    browser.runtime.sendNativeMessage(NATIVE_APP_ID, { type: "poll_command" })
      .then(function(response) {
        if (response && response.ok && response.data && response.data.hasCommand) {
          var command = response.data.command;
          executeCommand(command);
          // Poll again quickly for burst commands
          pollTimer = setTimeout(poll, 200);
        } else {
          // No command, poll at normal interval
          pollTimer = setTimeout(poll, POLL_INTERVAL_MS);
        }
      })
      .catch(function(err) {
        console.log("Agent Bridge poll error:", err);
        // Back off on error
        pollTimer = setTimeout(poll, POLL_INTERVAL_DISABLED_MS);
      });
  }

  function executeCommand(command) {
    if (!command || !command.action) {
      sendResult(command ? command.id : "", { ok: false, error: "Invalid command" });
      return;
    }

    if (ALLOWED_ACTIONS.indexOf(command.action) === -1) {
      sendResult(command.id, { ok: false, error: "Action not allowed" });
      return;
    }

    browser.tabs.query({ active: true, currentWindow: true }).then(function(tabs) {
      if (!tabs || tabs.length === 0) {
        sendResult(command.id, { ok: false, error: "No active tab" });
        return;
      }
      var tabId = tabs[0].id;
      browser.tabs.sendMessage(tabId, { action: command.action, params: command.params || {} })
        .then(function(response) {
          sendResult(command.id, response || { ok: false, error: "No response from content" });
        })
        .catch(function(err) {
          sendResult(command.id, { ok: false, error: "Content script error: " + (err.message || err) });
        });
    }).catch(function(err) {
      sendResult(command.id, { ok: false, error: "Tab query failed: " + (err.message || err) });
    });
  }

  function sendResult(commandId, result) {
    browser.runtime.sendNativeMessage(NATIVE_APP_ID, {
      type: "command_result",
      commandId: commandId || "",
      result: result
    }).catch(function(err) {
      console.log("Agent Bridge: failed to send result:", err);
    });
  }

  // Start polling when background script loads
  startPolling();
})();
