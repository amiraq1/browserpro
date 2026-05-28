package com.amiraq.nabd.agent

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.amiraq.nabd.R
import com.amiraq.nabd.settings.SettingsRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject

class AgentControlActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository
    private lateinit var statusText: TextView
    private lateinit var pageTitle: TextView
    private lateinit var pageUrl: TextView
    private lateinit var resultText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var pendingCommandId: String? = null
    private var pollCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agent_control)

        settings = SettingsRepository(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.agentToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.agent_control_title)

        statusText = findViewById(R.id.agentStatusText)
        pageTitle = findViewById(R.id.agentPageTitle)
        pageUrl = findViewById(R.id.agentPageUrl)
        resultText = findViewById(R.id.agentResultText)

        findViewById<MaterialButton>(R.id.btnGetPageInfo).setOnClickListener { executeAction("get_page_info") }
        findViewById<MaterialButton>(R.id.btnGetDom).setOnClickListener { executeAction("get_dom") }
        findViewById<MaterialButton>(R.id.btnScrollDown).setOnClickListener { executeAction("scroll", JSONObject().put("direction", "down")) }
        findViewById<MaterialButton>(R.id.btnScrollUp).setOnClickListener { executeAction("scroll", JSONObject().put("direction", "up")) }

        refreshStatus()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshStatus() {
        val policy = AgentBridgePolicy.fromSettings(settings)
        val sb = StringBuilder()
        sb.appendLine("الجسر: ${if (policy.isEnabled) "✅ مفعل" else "❌ معطل"}")
        sb.appendLine("قراءة DOM: ${if (policy.allowDomRead) "✅" else "❌"}")
        sb.appendLine("الضغط: ${if (policy.allowClick) "✅" else "❌"}")
        sb.appendLine("الكتابة: ${if (policy.allowTyping) "✅" else "❌"}")
        sb.appendLine("التمرير: ${if (policy.allowScroll) "✅" else "❌"}")
        sb.append("إرسال النماذج: ${if (policy.allowSubmit) "✅" else "❌"}")
        statusText.text = sb.toString()
    }

    private fun executeAction(action: String, params: JSONObject = JSONObject()) {
        val commandId = AgentBridgeCommandQueue.enqueue(action, params, settings)
        if (commandId == null) {
            val policy = AgentBridgePolicy.fromSettings(settings)
            val msg = if (!policy.isEnabled) "الجسر معطل" else "الإجراء مرفوض أو محدود"
            Snackbar.make(resultText, msg, Snackbar.LENGTH_SHORT).show()
            return
        }

        pendingCommandId = commandId
        pollCount = 0
        resultText.text = "⏳ جاري التنفيذ... ($action)"
        pollForResult()
    }

    private fun pollForResult() {
        if (pendingCommandId == null) return
        pollCount++

        val result = AgentBridgeCommandQueue.getResult(pendingCommandId!!)
        if (result != null) {
            displayResult(result)
            pendingCommandId = null
            return
        }

        if (pollCount > 20) { // 10 seconds max
            resultText.text = "⏱ انتهت المهلة — لم يتم استلام نتيجة"
            pendingCommandId = null
            return
        }

        handler.postDelayed({ pollForResult() }, 500)
    }

    private fun displayResult(result: AgentCommandResult) {
        val sb = StringBuilder()
        sb.appendLine(if (result.ok) "✅ نجح" else "❌ فشل")
        if (result.error != null) sb.appendLine("خطأ: ${result.error}")
        if (result.data != null) {
            val json = result.data.toString(2)
            sb.appendLine(json.take(2000))

            // Update page info if available
            if (result.data.has("title")) {
                pageTitle.text = result.data.optString("title", "—")
                pageUrl.text = result.data.optString("url", "—")
            }
        }
        resultText.text = sb.toString()
    }
}
