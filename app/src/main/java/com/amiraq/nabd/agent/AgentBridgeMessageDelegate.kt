package com.amiraq.nabd.agent

import android.util.Log
import com.amiraq.nabd.settings.SettingsRepository
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension

/**
 * Handles native messages from the Agent Bridge WebExtension.
 *
 * Supports three message types:
 * 1. poll_command — extension polls for pending commands from the queue
 * 2. command_result — extension reports result of an executed command
 * 3. direct action — validates against policy (legacy/direct flow)
 */
class AgentBridgeMessageDelegate(
    private val settings: SettingsRepository
) : WebExtension.MessageDelegate {

    override fun onMessage(
        nativeApp: String,
        message: Any,
        sender: WebExtension.MessageSender
    ): GeckoResult<Any>? {
        if (nativeApp != NATIVE_APP_ID) {
            return errorResult("Unknown native app: $nativeApp")
        }

        val json = try {
            message as? JSONObject
        } catch (e: Exception) {
            null
        }

        if (json == null) {
            return errorResult("Invalid message format")
        }

        val type = json.optString("type", "")

        return when (type) {
            "poll_command" -> handlePollCommand()
            "command_result" -> handleCommandResult(json)
            else -> handleDirectAction(json)
        }
    }

    /**
     * Extension polls for the next queued command.
     */
    private fun handlePollCommand(): GeckoResult<Any> {
        val policy = AgentBridgePolicy.fromSettings(settings)
        if (!policy.isEnabled) {
            val response = JSONObject()
                .put("ok", true)
                .put("data", JSONObject().put("hasCommand", false))
            return GeckoResult.fromValue(response)
        }

        val nextCommand = AgentBridgeCommandQueue.getNextCommand()
        val data = JSONObject()
        if (nextCommand != null) {
            data.put("hasCommand", true)
            data.put("command", nextCommand)
        } else {
            data.put("hasCommand", false)
        }

        val response = JSONObject().put("ok", true).put("data", data)
        return GeckoResult.fromValue(response)
    }

    /**
     * Extension reports the result of an executed command.
     */
    private fun handleCommandResult(json: JSONObject): GeckoResult<Any> {
        val commandId = json.optString("commandId", "")
        val result = json.optJSONObject("result")

        if (commandId.isBlank() || result == null) {
            return errorResult("Invalid command_result format")
        }

        AgentBridgeCommandQueue.completeCommand(commandId, result)

        val response = JSONObject().put("ok", true).put("data", "result_stored")
        return GeckoResult.fromValue(response)
    }

    /**
     * Direct action message — validates policy and acknowledges.
     */
    private fun handleDirectAction(json: JSONObject): GeckoResult<Any> {
        val action = json.optString("action", "")
        if (action.isBlank()) {
            return errorResult("Missing action")
        }

        val policy = AgentBridgePolicy.fromSettings(settings)
        if (!policy.isEnabled) {
            return errorResult("Agent bridge disabled")
        }
        if (!policy.isActionAllowed(action)) {
            return errorResult("Action not permitted: $action")
        }

        Log.d(TAG, "Agent bridge action allowed: $action")
        val response = JSONObject().put("ok", true).put("data", "action_permitted")
        return GeckoResult.fromValue(response)
    }

    private fun errorResult(error: String): GeckoResult<Any> {
        Log.w(TAG, "Agent bridge rejected: $error")
        val response = JSONObject().put("ok", false).put("error", error)
        return GeckoResult.fromValue(response)
    }

    companion object {
        private const val TAG = "AgentBridge"
        const val NATIVE_APP_ID = "browserpro_agent"
    }
}
