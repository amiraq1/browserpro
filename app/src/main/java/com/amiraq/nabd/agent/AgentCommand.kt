package com.amiraq.nabd.agent

import org.json.JSONObject
import java.util.UUID

data class AgentCommand(
    val id: String = UUID.randomUUID().toString(),
    val action: String,
    val params: JSONObject = JSONObject(),
    val createdAt: Long = System.currentTimeMillis()
)
