package com.amiraq.nabd.settings

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.amiraq.nabd.R
import com.amiraq.nabd.theme.ThemeManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SettingsActivity : AppCompatActivity() {

    private lateinit var repository: SettingsRepository

    private lateinit var toolbar: MaterialToolbar
    private lateinit var themeButton: MaterialButton
    private lateinit var remoteSwitch: MaterialSwitch
    private lateinit var adBlockSwitch: MaterialSwitch
    private lateinit var trackerSwitch: MaterialSwitch
    private lateinit var cryptominerSwitch: MaterialSwitch
    private lateinit var fingerprinterSwitch: MaterialSwitch
    private lateinit var endpointLayout: TextInputLayout
    private lateinit var endpointInput: TextInputEditText
    private lateinit var languageLayout: TextInputLayout
    private lateinit var languageInput: TextInputEditText
    private lateinit var maxLengthLayout: TextInputLayout
    private lateinit var maxLengthInput: TextInputEditText
    private lateinit var saveButton: MaterialButton
    private lateinit var resetButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        repository = SettingsRepository(this)
        bindViews()
        setupToolbar()
        loadSettings()
        setupListeners()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun bindViews() {
        toolbar = findViewById(R.id.settingsToolbar)
        themeButton = findViewById(R.id.themeButton)
        adBlockSwitch = findViewById(R.id.adBlockSwitch)
        trackerSwitch = findViewById(R.id.trackerSwitch)
        cryptominerSwitch = findViewById(R.id.cryptominerSwitch)
        fingerprinterSwitch = findViewById(R.id.fingerprinterSwitch)
        remoteSwitch = findViewById(R.id.remoteSwitch)
        endpointLayout = findViewById(R.id.endpointLayout)
        endpointInput = findViewById(R.id.endpointInput)
        languageLayout = findViewById(R.id.languageLayout)
        languageInput = findViewById(R.id.languageInput)
        maxLengthLayout = findViewById(R.id.maxLengthLayout)
        maxLengthInput = findViewById(R.id.maxLengthInput)
        saveButton = findViewById(R.id.saveButton)
        resetButton = findViewById(R.id.resetButton)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)
    }

    private fun loadSettings() {
        remoteSwitch.isChecked = repository.isRemoteSummarizerEnabled()
        endpointInput.setText(repository.getSummarizerEndpoint())
        languageInput.setText(repository.getSummaryLanguage())
        maxLengthInput.setText(repository.getSummaryMaxLength().toString())
        adBlockSwitch.isChecked = repository.isAdBlockEnabled()
        trackerSwitch.isChecked = repository.isTrackerProtectionEnabled()
        cryptominerSwitch.isChecked = repository.isCryptominerBlockingEnabled()
        fingerprinterSwitch.isChecked = repository.isFingerprinterBlockingEnabled()
        updateFieldsEnabled()
        updateThemeButtonLabel()
    }

    private fun setupListeners() {
        remoteSwitch.setOnCheckedChangeListener { _, _ ->
            updateFieldsEnabled()
            clearErrors()
        }
        themeButton.setOnClickListener { showThemeChooser() }
        saveButton.setOnClickListener { saveSettings() }
        resetButton.setOnClickListener { resetSettings() }
    }

    private fun updateFieldsEnabled() {
        val enabled = remoteSwitch.isChecked
        endpointInput.isEnabled = enabled
        languageInput.isEnabled = enabled
        maxLengthInput.isEnabled = enabled
    }

    private fun updateThemeButtonLabel() {
        val mode = repository.getThemeMode()
        val label = when (mode) {
            ThemeManager.THEME_LIGHT -> getString(R.string.theme_light)
            ThemeManager.THEME_DARK -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }
        themeButton.text = label
    }

    private fun showThemeChooser() {
        val options = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val modes = arrayOf(ThemeManager.THEME_SYSTEM, ThemeManager.THEME_LIGHT, ThemeManager.THEME_DARK)
        val current = repository.getThemeMode()
        val checkedIndex = modes.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.setting_theme)
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                val selected = modes[which]
                repository.setThemeMode(selected)
                ThemeManager.applyThemeMode(selected)
                updateThemeButtonLabel()
                Snackbar.make(themeButton, R.string.theme_updated, Snackbar.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }

    private fun saveSettings() {
        clearErrors()

        val useRemote = remoteSwitch.isChecked
        val endpoint = endpointInput.text?.toString().orEmpty().trim()
        val language = languageInput.text?.toString().orEmpty().trim()
        val maxLengthStr = maxLengthInput.text?.toString().orEmpty().trim()

        if (useRemote) {
            if (endpoint.isBlank()) {
                endpointLayout.error = getString(R.string.error_endpoint_empty)
                return
            }
            if (!isValidEndpoint(endpoint)) {
                endpointLayout.error = getString(R.string.error_endpoint_invalid)
                return
            }
        }

        if (language.isBlank()) {
            languageLayout.error = getString(R.string.error_language_empty)
            return
        }

        val maxLength = maxLengthStr.toIntOrNull()
        if (maxLength == null || maxLength < MIN_MAX_LENGTH || maxLength > MAX_MAX_LENGTH) {
            maxLengthLayout.error = getString(R.string.error_max_length_invalid, MIN_MAX_LENGTH, MAX_MAX_LENGTH)
            return
        }

        repository.setRemoteSummarizerEnabled(useRemote)
        repository.setSummarizerEndpoint(endpoint)
        repository.setSummaryLanguage(language)
        repository.setSummaryMaxLength(maxLength)
        repository.setAdBlockEnabled(adBlockSwitch.isChecked)
        repository.setTrackerProtectionEnabled(trackerSwitch.isChecked)
        repository.setCryptominerBlockingEnabled(cryptominerSwitch.isChecked)
        repository.setFingerprinterBlockingEnabled(fingerprinterSwitch.isChecked)

        Snackbar.make(saveButton, R.string.settings_saved, Snackbar.LENGTH_SHORT).show()
    }

    private fun resetSettings() {
        repository.resetToDefaults()
        ThemeManager.applyThemeMode(ThemeManager.THEME_SYSTEM)
        loadSettings()
        clearErrors()
        Snackbar.make(resetButton, R.string.settings_reset, Snackbar.LENGTH_SHORT).show()
    }

    private fun clearErrors() {
        endpointLayout.error = null
        languageLayout.error = null
        maxLengthLayout.error = null
    }

    private fun isValidEndpoint(url: String): Boolean {
        if (url.startsWith("https://")) return true
        if (url.startsWith("http://")) {
            val localPrefixes = listOf("http://localhost", "http://127.0.0.1", "http://10.0.2.2")
            return localPrefixes.any { url.startsWith(it) }
        }
        return false
    }

    companion object {
        private const val MIN_MAX_LENGTH = 100
        private const val MAX_MAX_LENGTH = 2000
    }
}
