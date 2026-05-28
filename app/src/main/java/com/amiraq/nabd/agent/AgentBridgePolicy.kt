package com.amiraq.nabd.agent

import com.amiraq.nabd.settings.SettingsRepository

/**
 * Policy that controls which Agent Bridge actions are permitted.
 * All capabilities default to OFF for security.
 */
data class AgentBridgePolicy(
    val isEnabled: Boolean = false,
    val allowDomRead: Boolean = false,
    val allowClick: Boolean = false,
    val allowTyping: Boolean = false,
    val allowScroll: Boolean = false,
    val allowSubmit: Boolean = false
) {
    /**
     * Checks if a given action is allowed by the current policy.
     */
    fun isActionAllowed(action: String): Boolean {
        if (!isEnabled) return false
        return when (action) {
            "get_dom", "get_page_info" -> allowDomRead
            "click" -> allowClick
            "input_text" -> allowTyping
            "scroll" -> allowScroll
            "submit_form" -> allowSubmit
            else -> false
        }
    }

    companion object {
        /**
         * Loads policy from persisted settings.
         */
        fun fromSettings(settings: SettingsRepository): AgentBridgePolicy {
            return AgentBridgePolicy(
                isEnabled = settings.isAgentBridgeEnabled(),
                allowDomRead = settings.isAgentDomReadAllowed(),
                allowClick = settings.isAgentClickAllowed(),
                allowTyping = settings.isAgentTypingAllowed(),
                allowScroll = settings.isAgentScrollAllowed(),
                allowSubmit = settings.isAgentSubmitAllowed()
            )
        }
    }
}
