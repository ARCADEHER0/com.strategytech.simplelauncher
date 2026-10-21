package com.strategytech.simplelauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts

/** Caregiver settings. Reached by pressing and holding the gear on the home screen. */
class SettingsActivity : BaseActivity() {

    private lateinit var prefs: Prefs
    private lateinit var contactNameInput: EditText
    private lateinit var contactNumberInput: EditText
    private lateinit var emergencyNumberInput: EditText

    private val homeRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            HomeRole.onResult(this, result)
            updateHomeStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_settings)
        findViewById<View>(R.id.root).applySystemBarPadding()

        contactNameInput = findViewById(R.id.contactNameInput)
        contactNumberInput = findViewById(R.id.contactNumberInput)
        emergencyNumberInput = findViewById(R.id.emergencyNumberInput)

        if (savedInstanceState == null) {
            contactNameInput.setText(prefs.contactName)
            contactNumberInput.setText(prefs.contactNumber)
            emergencyNumberInput.setText(prefs.emergencyNumber)
        }

        val sizeGroup = findViewById<RadioGroup>(R.id.textSizeGroup)
        sizeGroup.check(
            when (prefs.textSizeIndex) {
                1 -> R.id.sizeLarge
                2 -> R.id.sizeXLarge
                else -> R.id.sizeNormal
            }
        )
        sizeGroup.setOnCheckedChangeListener { _, checkedId ->
            val index = when (checkedId) {
                R.id.sizeLarge -> 1
                R.id.sizeXLarge -> 2
                else -> 0
            }
            if (index != prefs.textSizeIndex) {
                saveFields()
                prefs.textSizeIndex = index
                recreate() // re-draw this screen at the new size
            }
        }

        findViewById<Button>(R.id.homeButton).setOnClickListener {
            HomeRole.request(this, homeRoleLauncher)
        }
        findViewById<Button>(R.id.phoneSettingsButton).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_LONG).show()
            }
        }
        findViewById<Button>(R.id.doneButton).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        updateHomeStatus()
    }

    override fun onPause() {
        saveFields()
        super.onPause()
    }

    private fun saveFields() {
        prefs.contactName = contactNameInput.text.toString()
        prefs.contactNumber = contactNumberInput.text.toString()
        // Store nothing while the field still shows the regional default (911, 112, ...),
        // so it keeps following the phone's region. A blank value also means "use the default".
        val typed = emergencyNumberInput.text.toString().dialable()
        prefs.emergencyNumber = if (typed == prefs.defaultEmergencyNumber()) "" else typed
    }

    private fun updateHomeStatus() {
        val isHome = HomeRole.isDefaultHome(this)
        findViewById<TextView>(R.id.homeStatus)
            .setText(if (isHome) R.string.home_status_yes else R.string.home_status_no)
        findViewById<Button>(R.id.homeButton)
            .setText(if (isHome) R.string.change_home else R.string.make_home)
    }
}
