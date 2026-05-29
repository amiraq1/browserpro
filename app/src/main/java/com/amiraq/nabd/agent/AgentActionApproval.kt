package com.amiraq.nabd.agent

import org.json.JSONObject

enum class RiskLevel { LOW, MEDIUM, HIGH }

data class AgentActionApproval(
    val action: String,
    val params: JSONObject = JSONObject(),
    val riskLevel: RiskLevel,
    val reason: String
)

object AgentApprovalManager {

    fun getRiskLevel(action: String): RiskLevel = when (action) {
        "get_dom", "get_page_info", "scroll" -> RiskLevel.LOW
        "click" -> RiskLevel.MEDIUM
        "input_text", "submit_form" -> RiskLevel.HIGH
        else -> RiskLevel.HIGH
    }

    fun requiresApproval(action: String): Boolean =
        getRiskLevel(action) != RiskLevel.LOW

    fun buildApprovalMessage(action: String, params: JSONObject): String {
        val risk = getRiskLevel(action)
        val riskAr = when (risk) {
            RiskLevel.LOW -> "منخفض"
            RiskLevel.MEDIUM -> "متوسط"
            RiskLevel.HIGH -> "عالي"
        }
        return when (action) {
            "click" -> "الوكيل يريد الضغط على عنصر (خطورة: $riskAr)"
            "input_text" -> "الوكيل يريد كتابة نص: \"${params.optString("text", "").take(50)}\" (خطورة: $riskAr)"
            "submit_form" -> "الوكيل يريد إرسال نموذج (خطورة: $riskAr)"
            else -> "الوكيل يريد تنفيذ: $action (خطورة: $riskAr)"
        }
    }
}
