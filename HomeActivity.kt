package com.strategytech.simplelauncher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts

/** The home screen: clock, four big buttons, and the "My Apps" tray. */
class HomeActivity : BaseActivity() {

    private class Tile(
        val iconRes: Int,
        val labelRes: Int,
        val subRes: Int,
        val emergency: Boolean,
        val action: () -> Unit
    )

    private lateinit var prefs: Prefs
    private var textScaleAtCreate = 1.0f

    private lateinit var scroll: ScrollView
    private lateinit var tileGrid: LinearLayout
    private lateinit var appTray: LinearLayout
    private lateinit var trayScroll: View
    private lateinit var emptyHint: View
    private lateinit var setupBanner: View

    private val homeRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateSetupBanner()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        textScaleAtCreate = prefs.textScale
        setContentView(R.layout.activity_home)

        scroll = findViewById(R.id.root)
        scroll.applySystemBarPadding()
        tileGrid = findViewById(R.id.tileGrid)
        appTray = findViewById(R.id.appTray)
        trayScroll = findViewById(R.id.trayScroll)
        emptyHint = findViewById(R.id.emptyHint)
        setupBanner = findViewById(R.id.setupBanner)

        // This is the bottom of the stack. Back (button or gesture) must simply do nothing.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // stay on the home screen
            }
        })

        findViewById<Button>(R.id.setupButton).setOnClickListener {
            HomeRole.request(this, homeRoleLauncher)
        }
        findViewById<Button>(R.id.addAppButton).setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }

        // Settings are for a family member: they need a deliberate press-and-hold, so a
        // shaky tap can't change anything.
        findViewById<View>(R.id.settingsButton).apply {
            setOnClickListener {
                Toast.makeText(this@HomeActivity, R.string.hold_for_settings, Toast.LENGTH_LONG).show()
            }
            setOnLongClickListener {
                startActivity(Intent(this@HomeActivity, SettingsActivity::class.java))
                true
            }
        }

        buildTiles()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.textScale != textScaleAtCreate) {
            recreate() // caregiver changed the text size
            return
        }
        updateTray()
        updateSetupBanner()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while already home: scroll back to the top.
        scroll.smoothScrollTo(0, 0)
    }

    // ---------------------------------------------------------------- big buttons

    private fun buildTiles() {
        val tiles = listOf(
            Tile(R.string.tile_phone_icon, R.string.tile_phone, R.string.tile_phone_sub, false) { openDialer() },
            Tile(R.string.tile_messages_icon, R.string.tile_messages, R.string.tile_messages_sub, false) { openMessages() },
            Tile(R.string.tile_photos_icon, R.string.tile_photos, R.string.tile_photos_sub, false) { openPhotos() },
            Tile(R.string.tile_help_icon, R.string.tile_help, R.string.tile_help_sub, true) { showEmergencyDialog() }
        )
        val columns = resources.getInteger(R.integer.tile_columns)

        tileGrid.removeAllViews()
        for (rowTiles in tiles.chunked(columns)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            for (tile in rowTiles) row.addView(createTile(row, tile))
            tileGrid.addView(row)
        }
    }

    private fun createTile(parent: LinearLayout, tile: Tile): View {
        val view = layoutInflater.inflate(R.layout.view_tile, parent, false)
        val margin = resources.getDimensionPixelSize(R.dimen.tile_margin)
        view.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(margin, margin, margin, margin)
        }
        view.findViewById<TextView>(R.id.tileIcon).setText(tile.iconRes)
        view.findViewById<TextView>(R.id.tileLabel).setText(tile.labelRes)
        view.findViewById<TextView>(R.id.tileSub).setText(tile.subRes)
        if (tile.emergency) view.setBackgroundResource(R.drawable.bg_tile_emergency)
        view.contentDescription = getString(tile.labelRes) + ". " + getString(tile.subRes)
        view.setOnClickListener { tile.action() }
        return view
    }

    private fun openDialer() {
        launchOrToast(Intent(Intent.ACTION_DIAL))
    }

    private fun openMessages() {
        launchOrToast(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
        )
    }

    private fun openPhotos() {
        launchOrToast(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY),
            Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        )
    }

    private fun launchOrToast(intent: Intent, fallback: Intent? = null) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            if (fallback != null) {
                try {
                    startActivity(fallback)
                    return
                } catch (e2: ActivityNotFoundException) {
                    // fall through to the message below
                }
            }
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------- emergency

    private fun showEmergencyDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_emergency, null)

        val emergencyNumber = prefs.emergencyNumber
        val callEmergency = view.findViewById<Button>(R.id.callEmergency)
        callEmergency.text = getString(R.string.call_emergency_fmt, emergencyNumber)

        val contactNumber = prefs.contactNumber
        val callContact = view.findViewById<Button>(R.id.callContact)
        val noContactHint = view.findViewById<View>(R.id.noContactHint)
        if (contactNumber.dialable().isNotEmpty()) {
            val name = prefs.contactName.ifBlank { getString(R.string.my_contact) }
            callContact.text = getString(R.string.call_contact_fmt, name)
            noContactHint.visibility = View.GONE
        } else {
            callContact.visibility = View.GONE
        }

        val dialog = Dialogs.showCustom(this, view)
        callEmergency.setOnClickListener {
            dialog.dismiss()
            dial(emergencyNumber)
        }
        callContact.setOnClickListener {
            dialog.dismiss()
            dial(contactNumber)
        }
        view.findViewById<Button>(R.id.cancelEmergency).setOnClickListener { dialog.dismiss() }
    }

    /** Opens the dialer with the number filled in. The person presses the green call button. */
    private fun dial(number: String) {
        val clean = number.dialable()
        if (clean.isEmpty()) return
        launchOrToast(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", clean, null)))
    }

    // ---------------------------------------------------------------- My Apps tray

    @Suppress("DEPRECATION")
    private fun updateTray() {
        appTray.removeAllViews()
        var shown = 0
        for (flat in prefs.favorites()) {
            val component = ComponentName.unflattenFromString(flat) ?: continue
            val info = try {
                packageManager.getActivityInfo(component, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                continue // app was uninstalled; just hide it
            }
            val label = info.loadLabel(packageManager).toString()
            val chip = layoutInflater.inflate(R.layout.item_app_chip, appTray, false)
            chip.findViewById<ImageView>(R.id.chipIcon).setImageDrawable(info.loadIcon(packageManager))
            chip.findViewById<TextView>(R.id.chipLabel).text = label
            chip.contentDescription = label
            chip.setOnClickListener { launchApp(component) }
            chip.setOnLongClickListener {
                confirmRemove(flat, label)
                true
            }
            appTray.addView(chip)
            shown++
        }
        emptyHint.visibility = if (shown == 0) View.VISIBLE else View.GONE
        trayScroll.visibility = if (shown == 0) View.GONE else View.VISIBLE
    }

    private fun launchApp(component: ComponentName) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmRemove(flat: String, label: String) {
        Dialogs.confirm(
            this,
            getString(R.string.remove_fmt, label),
            getString(R.string.remove_yes),
            getString(R.string.remove_no)
        ) {
            prefs.removeFavorite(flat)
            updateTray()
        }
    }

    // ---------------------------------------------------------------- "make this my home screen"

    private fun updateSetupBanner() {
        setupBanner.visibility = if (HomeRole.isDefaultHome(this)) View.GONE else View.VISIBLE
    }
}
