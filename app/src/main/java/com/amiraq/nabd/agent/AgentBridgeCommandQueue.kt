package com.amiraq.nabd.agent

import android.util.Log
import com.amiraq.nabd.settings.SettingsRepository
import org.json.JSONObject
import java.util.LinkedList

/**
 * Thread-safe command queue for the Agent Bridge.
 *
 * Future AI agent code enqueues commands here.
 * The WebExtension polls for pending commands via native messaging.
 * Results are stored when the extension reports back.
 *
 * Architecture:
 * 1. Kotlin AI agent calls enqueue*() methods
 * 2. Extension background.js polls with { type: "poll_command" }
 * 3. Kotlin returns next command from queue
 * 4. Extension executes via content.js
 * 5. Extension sends { type: "command_result", ... }
 * 6. Kotlin stores result
 */
object AgentBridgeCommandQueue {

    private const val TAG = "AgentCommandQueue"
    private const val MAX_QUEUE_SIZE = 20
    private const val DOM_READ_COOLDOWN_MS = 1000L

    private val queue = LinkedList<AgentCommand>()
    private val results = LinkedList<AgentCommandResult>()
    private var lastDomReadTime = 0L

    private val ALLOWED_ACTIONS = setOf(
        "get_dom", "click", "input_text", "submit_form", "scroll", "get_page_info"
    )

    /**
     * Enqueues a command after validating policy and rate limits.
     * Returns command ID on success, null on rejection.
     */
    @Synchronized
    fun enqueue(action: String, params: JSONObject, settings: SettingsRepository): String? {
        val policy = AgentBridgePolicy.fromSettings(settings)

        if (!policy.isEnabled) {
            Log.w(TAG, "Enqueue rejected: bridge disabled")
            return null
        }
        if (!policy.isActionAllowed(action)) {
            Log.w(TAG, "Enqueue rejected: action not permitted: $action")
            return null
        }
        if (action !in ALLOWED_ACTIONS) {
            Log.w(TAG, "Enqueue rejected: unknown action: $action")
            return null
        }
        if (queue.size >= MAX_QUEUE_SIZE) {
            Log.w(TAG, "Enqueue rejected: queue full")
            return null
        }

        // Rate limit DOM reads
        if (action == "get_dom") {
            val now = System.currentTimeMillis()
            if (now - lastDomReadTime < DOM_READ_COOLDOWN_MS) {
                Log.w(TAG, "Enqueue rejected: get_dom rate limited")
                return null
            }
            lastDomReadTime = now
        }

        val command = AgentCommand(action = action, params = params)
        queue.add(command)
        Log.d(TAG, "Enqueued: ${command.id} -> $action")
        return command.id
    }

    /**
     * Returns the next pending command as JSON, or null if empty.
     */
    @Synchronized
    fun getNextCommand(): JSONObject? {
        val command = queue.poll() ?: return null
        return JSONObject().apply {
            put("id", command.id)
            put("action", command.action)
            put("params", command.params)
        }
    }

    /**
     * Stores a command result from the extension.
     */
    @Synchronized
    fun completeCommand(commandId: String, result: JSONObject) {
        val agentResult = AgentCommandResult(
            commandId = commandId,
            ok = result.optBoolean("ok", false),
            data = if (result.has("data")) result.optJSONObject("data") else null,
            error = result.optString("error", "").ifBlank { null }
        )
        results.add(agentResult)
        // Keep results bounded
        while (results.size > MAX_QUEUE_SIZE) results.poll()
        Log.d(TAG, "Command completed: $commandId ok=${agentResult.ok}")
    }

    /**
     * Returns and removes the latest result for a command ID, or null.
     */
    @Synchronized
    fun getResult(commandId: String): AgentCommandResult? {
        val iter = results.iterator()
        while (iter.hasNext()) {
            val r = iter.next()
            if (r.commandId == commandId) { iter.remove(); return r }
        }
        return null
    }

    @Synchronized
    fun hasPendingCommands(): Boolean = queue.isNotEmpty()

    @Synchronized
    fun getPendingCommands(): List<AgentCommand> = queue.toList()

    @Synchronized
    fun getLatestResult(): AgentCommandResult? = results.peekLast()

    @Synchronized
    fun getRecentResults(limit: Int = 5): List<AgentCommandResult> =
        results.takeLast(limit.coerceAtMost(results.size))

    @Synchronized
    fun clear() { queue.clear(); results.clear() }

    // ─── Convenience methods for future AI agent code ────────────────────────────

    fun enqueueGetPageInfo(settings: SettingsRepository): String? =
        enqueue("get_page_info", JSONObject(), settings)

    fun enqueueGetDom(settings: SettingsRepository): String? =
        enqueue("get_dom", JSONObject(), settings)

    fun enqueueClick(id: Int, settings: SettingsRepository): String? =
        enqueue("click", JSONObject().put("id", id), settings)

    fun enqueueInputText(id: Int, text: String, settings: SettingsRepository): String? =
        enqueue("input_text", JSONObject().put("id", id).put("text", text), settings)

    fun enqueueScroll(direction: String, settings: SettingsRepository): String? =
        enqueue("scroll", JSONObject().put("direction", direction), settings)

    fun enqueueSubmitForm(id: Int, settings: SettingsRepository): String? =
        enqueue("submit_form", JSONObject().put("id", id), settings)
}
