package com.amiraq.nabd.agent

import org.json.JSONObject

data class AgentCommandResult(
    val commandId: String,
    val ok: Boolean,
    val data: JSONObject? = null,
    val error: String? = null,
    val completedAt: Long = System.currentTimeMillis()
)
