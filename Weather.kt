package com.strategytech.simplelauncher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Current weather for the phone's approximate location.
 *
 * Data: Open-Meteo.com (CC BY 4.0, free for non-commercial use). Only the approximate position,
 * rounded to two decimals (about 1 km), is sent. There is no account and no identifier.
 * The last good report is kept on the phone so the screen is never blank when offline.
 */
object Weather {

    class Report(
        val temp: Int,
        val high: Int,
        val low: Int,
        val code: Int,
        val isDay: Boolean,
        val fahrenheit: Boolean
    )

    class Look(val emoji: String, @StringRes val label: Int)

    private const val FILE = "weather_cache"
    private const val REFRESH_AFTER_MS = 30L * 60 * 1000
    private const val SHOW_FOR_MS = 6L * 60 * 60 * 1000
    private const val LOCATION_MAX_AGE_MS = 3L * 60 * 60 * 1000
    private const val LOCATION_TIMEOUT_MS = 20_000L

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)

    fun isRefreshing(): Boolean = busy.get()

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** True when the phone's Location switch is on. */
    fun locationOn(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    private fun store(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The saved report, or null when there is none or it is too old to be useful. */
    fun cached(context: Context): Report? {
        val sp = store(context)
        val savedAt = sp.getLong("saved_at", 0L)
        val age = System.currentTimeMillis() - savedAt
        if (savedAt <= 0L || age < 0L || age > SHOW_FOR_MS) return null
        return Report(
            temp = sp.getInt("temp", 0),
            high = sp.getInt("high", 0),
            low = sp.getInt("low", 0),
            code = sp.getInt("code", 3),
            isDay = sp.getBoolean("is_day", true),
            fahrenheit = sp.getBoolean("fahrenheit", false)
        )
    }

    fun needsRefresh(context: Context): Boolean {
        val savedAt = store(context).getLong("saved_at", 0L)
        val age = System.currentTimeMillis() - savedAt
        return savedAt <= 0L || age < 0L || age > REFRESH_AFTER_MS
    }

    /**
     * Looks up the position, downloads the weather and saves it. [onDone] runs on the main
     * thread when finished, whether or not it worked. Does nothing without the location permission.
     */
    fun refresh(context: Context, onDone: () -> Unit) {
        val app = context.applicationContext
        if (!hasPermission(app)) return
        if (!busy.compareAndSet(false, true)) return
        findLocation(app) { spot ->
            if (spot == null) {
                finish(onDone)
            } else {
                worker.execute {
                    try {
                        download(app, spot.latitude, spot.longitude)
                    } catch (e: Exception) {
                        // Offline or server hiccup: keep showing the last good report.
                    }
                    finish(onDone)
                }
            }
        }
    }

    private fun finish(onDone: () -> Unit) {
        busy.set(false)
        main.post(onDone)
    }

    // ---------------------------------------------------------------- position

    @SuppressLint("MissingPermission")
    private fun findLocation(context: Context, callback: (Location?) -> Unit) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            callback(null)
            return
        }
        try {
            val providers = manager.getProviders(true)
            val known = newestLastKnown(manager, providers)
            if (known != null && System.currentTimeMillis() - known.time < LOCATION_MAX_AGE_MS) {
                callback(known)
                return
            }
            val provider = when {
                Build.VERSION.SDK_INT >= 31 && providers.contains(LocationManager.FUSED_PROVIDER) ->
                    LocationManager.FUSED_PROVIDER
                providers.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                callback(known)
                return
            }
            val signal = CancellationSignal()
            val answered = AtomicBoolean(false)
            val giveUp = Runnable {
                if (answered.compareAndSet(false, true)) {
                    signal.cancel()
                    callback(known)
                }
            }
            main.postDelayed(giveUp, LOCATION_TIMEOUT_MS)
            LocationManagerCompat.getCurrentLocation(
                manager, provider, signal, ContextCompat.getMainExecutor(context)
            ) { fresh ->
                if (answered.compareAndSet(false, true)) {
                    main.removeCallbacks(giveUp)
                    callback(fresh ?: known)
                }
            }
        } catch (e: SecurityException) {
            callback(null)
        } catch (e: RuntimeException) {
            callback(null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun newestLastKnown(manager: LocationManager, providers: List<String>): Location? {
        var best: Location? = null
        for (name in providers) {
            val found = try {
                manager.getLastKnownLocation(name)
            } catch (e: SecurityException) {
                null // for example GPS, which needs the "precise" permission we do not ask for
            }
            if (found != null && (best == null || found.time > best.time)) best = found
        }
        return best
    }

    // ---------------------------------------------------------------- download

    private fun usesFahrenheit(): Boolean =
        Locale.getDefault().country.uppercase(Locale.ROOT) in
            setOf("US", "BS", "BZ", "KY", "LR", "PW", "FM", "MH")

    private fun download(context: Context, latitude: Double, longitude: Double) {
        val fahrenheit = usesFahrenheit()
        val address = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&temperature_unit=%s&timezone=auto&forecast_days=1",
            latitude,
            longitude,
            if (fahrenheit) "fahrenheit" else "celsius"
        )
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "SimpleLauncher/1.0 (Android)")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val current = root.getJSONObject("current")
            val daily = root.getJSONObject("daily")
            val temp = current.getDouble("temperature_2m").roundToInt()
            val high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt()
            val low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()
            store(context).edit()
                .putInt("temp", temp)
                .putInt("high", high)
                .putInt("low", low)
                .putInt("code", current.optInt("weather_code", 3))
                .putBoolean("is_day", current.optInt("is_day", 1) == 1)
                .putBoolean("fahrenheit", fahrenheit)
                .putLong("saved_at", System.currentTimeMillis())
                .apply()
        } finally {
            connection.disconnect()
        }
    }

    // ---------------------------------------------------------------- how it looks

    // Emoji written as escapes so the file stays plain ASCII.
    private const val SUN = "☀️"
    private const val MOON = "🌙"
    private const val SUN_SMALL_CLOUD = "🌤️"
    private const val SUN_CLOUD = "⛅"
    private const val CLOUD = "☁️"
    private const val FOG = "🌫️"
    private const val SUN_RAIN = "🌦️"
    private const val RAIN = "🌧️"
    private const val SNOW = "🌨️"
    private const val STORM = "⛈️"

    /** Maps an Open-Meteo (WMO) weather code to a picture and a plain-English word. */
    fun look(report: Report): Look {
        val day = report.isDay
        return when (report.code) {
            0 -> if (day) Look(SUN, R.string.weather_sunny) else Look(MOON, R.string.weather_clear)
            1 -> if (day) Look(SUN_SMALL_CLOUD, R.string.weather_mostly_sunny) else Look(MOON, R.string.weather_mostly_clear)
            2 -> if (day) Look(SUN_CLOUD, R.string.weather_partly_cloudy) else Look(CLOUD, R.string.weather_partly_cloudy)
            3 -> Look(CLOUD, R.string.weather_cloudy)
            45, 48 -> Look(FOG, R.string.weather_fog)
            51, 53, 55 -> Look(SUN_RAIN, R.string.weather_drizzle)
            56, 57, 66, 67 -> Look(RAIN, R.string.weather_freezing_rain)
            61, 63, 65 -> Look(RAIN, R.string.weather_rain)
            71, 73, 75, 77 -> Look(SNOW, R.string.weather_snow)
            80, 81, 82 -> Look(SUN_RAIN, R.string.weather_showers)
            85, 86 -> Look(SNOW, R.string.weather_snow_showers)
            95, 96, 99 -> Look(STORM, R.string.weather_thunderstorm)
            else -> Look(CLOUD, R.string.weather_cloudy)
        }
    }
}
