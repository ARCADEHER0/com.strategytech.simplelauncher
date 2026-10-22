package com.strategytech.simplelauncher

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
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
import androidx.core.content.ContextCompat

/** The home screen: weather, clock, three big buttons, a slim Emergency bar, and the "My Apps" tray. */
class HomeActivity : BaseActivity() {

    private class Tile(
        val iconRes: Int,
        val labelRes: Int,
        val subRes: Int,
        val action: () -> Unit
    )

    private lateinit var prefs: Prefs
    private var textScaleAtCreate = 1.0f
    private var currentDialog: AlertDialog? = null

    private lateinit var scroll: ScrollView
    private lateinit var tileGrid: LinearLayout
    private lateinit var appTray: LinearLayout
    private lateinit var trayScroll: View
    private lateinit var emptyHint: View
    private lateinit var setupBanner: View
    private lateinit var weatherBlock: View
    private lateinit var weatherMain: TextView
    private lateinit var weatherDetail: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val weatherTick = object : Runnable {
        override fun run() {
            refreshWeather(force = false)
            handler.postDelayed(this, WEATHER_CHECK_MS)
        }
    }

    private val homeRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            HomeRole.onResult(this, result)
            updateSetupBanner()
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshWeather(force = true)
            updateWeatherUi()
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
        weatherBlock = findViewById(R.id.weatherBlock)
        weatherMain = findViewById(R.id.weatherMain)
        weatherDetail = findViewById(R.id.weatherDetail)

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

        findViewById<View>(R.id.emergencyBar).apply {
            contentDescription = getString(R.string.tile_help) + ". " + getString(R.string.tile_help_sub)
            setOnClickListener { showEmergencyDialog() }
        }

        weatherBlock.setOnClickListener { onWeatherTapped() }

        buildTiles()

        // First time only: explain, then let Android ask for location (weather) and notifications.
        if (!prefs.introShown) {
            scroll.post {
                if (!isFinishing && !isDestroyed) showPermissionIntro()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (prefs.textScale != textScaleAtCreate) {
            recreate() // caregiver changed the text size
            return
        }
        updateTray()
        updateSetupBanner()
        updateWeatherUi()
        handler.removeCallbacks(weatherTick)
        handler.post(weatherTick)
    }

    override fun onPause() {
        handler.removeCallbacks(weatherTick)
        super.onPause()
    }

    override fun onDestroy() {
        currentDialog?.dismiss()
        currentDialog = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while already home: scroll back to the top.
        scroll.smoothScrollTo(0, 0)
    }

    // ---------------------------------------------------------------- big buttons

    private fun buildTiles() {
        val tiles = listOf(
            Tile(R.string.tile_phone_icon, R.string.tile_phone, R.string.tile_phone_sub) { openDialer() },
            Tile(R.string.tile_messages_icon, R.string.tile_messages, R.string.tile_messages_sub) { openMessages() },
            Tile(R.string.tile_photos_icon, R.string.tile_photos, R.string.tile_photos_sub) { openPhotos() }
        )
        val columns = resources.getInteger(R.integer.tile_columns)

        // Upright: Phone Call gets the whole first row, Messages and Photos share the second.
        // Sideways: all three in one row.
        val rows: List<List<Tile>> =
            if (columns <= 2) listOf(tiles.take(1), tiles.drop(1)) else tiles.chunked(columns)

        tileGrid.removeAllViews()
        for (rowTiles in rows) {
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
        // Each app gets its own task, so pressing Home never destroys it or loses a half-typed text.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        fallback?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    // ---------------------------------------------------------------- weather

    private fun refreshWeather(force: Boolean) {
        if (!Weather.hasPermission(this)) return
        if (!force && !Weather.needsRefresh(this)) return
        Weather.refresh(this) {
            if (!isFinishing && !isDestroyed) updateWeatherUi()
        }
    }

    private fun updateWeatherUi() {
        if (!Weather.hasPermission(this)) {
            weatherMain.setText(R.string.weather_title)
            weatherDetail.setText(R.string.weather_tap_to_enable)
        } else {
            val report = Weather.cached(this)
            if (report == null) {
                weatherMain.setText(R.string.weather_title)
                weatherDetail.setText(
                    when {
                        !Weather.locationOn(this) -> R.string.weather_need_location
                        Weather.isRefreshing() -> R.string.weather_loading
                        else -> R.string.weather_unavailable
                    }
                )
            } else {
                val look = Weather.look(report)
                val unit = if (report.fahrenheit) "F" else "C"
                weatherMain.text = getString(R.string.weather_main_fmt, look.emoji, report.temp, unit)
                weatherDetail.text = getString(
                    R.string.weather_detail_fmt, getString(look.label), report.high, report.low
                )
            }
        }
        weatherBlock.contentDescription =
            weatherMain.text.toString() + ". " + weatherDetail.text.toString().replace('\n', ' ')
    }

    private fun onWeatherTapped() {
        if (Weather.hasPermission(this)) {
            refreshWeather(force = true)
            updateWeatherUi()
            return
        }
        // After Android has refused to ask again, the only way to turn it on is the app's settings page.
        val blocked = prefs.permissionRequests > 0 &&
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (blocked) openAppSettings() else requestPermissions()
    }

    private fun showPermissionIntro() {
        currentDialog = Dialogs.confirm(
            this,
            getString(R.string.permission_intro),
            getString(R.string.permission_continue),
            getString(R.string.permission_later),
            yesBackground = R.drawable.bg_button_green,
            onNo = { prefs.introShown = true }
        ) {
            prefs.introShown = true
            requestPermissions()
        }
    }

    /** Asks Android for location (weather) and, on Android 13+, notifications. */
    private fun requestPermissions() {
        val wanted = mutableListOf<String>()
        if (!Weather.hasPermission(this)) wanted.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (wanted.isEmpty()) return
        prefs.permissionRequests = prefs.permissionRequests + 1
        permissionLauncher.launch(wanted.toTypedArray())
    }

    private fun openAppSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        } catch (e: ActivityNotFoundException) {
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
        currentDialog = dialog
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
        currentDialog = Dialogs.confirm(
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

    private companion object {
        const val WEATHER_CHECK_MS = 10L * 60 * 1000
    }
}
