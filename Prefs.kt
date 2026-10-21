package com.strategytech.simplelauncher

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/** All settings live in private on-device storage. Nothing is ever sent anywhere. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        (context.applicationContext ?: context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var contactName: String
        get() = sp.getString(KEY_CONTACT_NAME, "").orEmpty()
        set(value) = sp.edit().putString(KEY_CONTACT_NAME, value.trim()).apply()

    var contactNumber: String
        get() = sp.getString(KEY_CONTACT_NUMBER, "").orEmpty()
        set(value) = sp.edit().putString(KEY_CONTACT_NUMBER, value.trim()).apply()

    /** Number the red Emergency button dials. Defaults by region; the caregiver can change it. */
    var emergencyNumber: String
        get() = sp.getString(KEY_EMERGENCY_NUMBER, null)?.takeIf { it.isNotBlank() }
            ?: defaultEmergencyNumber()
        set(value) = sp.edit().putString(KEY_EMERGENCY_NUMBER, value.trim()).apply()

    /** 0 = normal, 1 = large, 2 = extra large. */
    var textSizeIndex: Int
        get() = sp.getInt(KEY_TEXT_SIZE, 0).coerceIn(0, 2)
        set(value) = sp.edit().putInt(KEY_TEXT_SIZE, value.coerceIn(0, 2)).apply()

    val textScale: Float
        get() = when (textSizeIndex) {
            1 -> 1.2f
            2 -> 1.4f
            else -> 1.0f
        }

    /** Favorite apps, stored as flattened component names ("package/class"), in the order added. */
    fun favorites(): List<String> =
        sp.getString(KEY_FAVORITES, "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun addFavorite(flat: String) {
        val list = favorites()
        if (flat !in list) saveFavorites(list + flat)
    }

    fun removeFavorite(flat: String) {
        saveFavorites(favorites() - flat)
    }

    private fun saveFavorites(list: List<String>) {
        sp.edit().putString(KEY_FAVORITES, list.joinToString("\n")).apply()
    }

    fun defaultEmergencyNumber(): String =
        when (Locale.getDefault().country.uppercase(Locale.ROOT)) {
            "US", "CA" -> "911"
            "GB" -> "999"
            "AU" -> "000"
            "NZ" -> "111"
            else -> "112"
        }

    private companion object {
        const val FILE = "simple_launcher"
        const val KEY_CONTACT_NAME = "contact_name"
        const val KEY_CONTACT_NUMBER = "contact_number"
        const val KEY_EMERGENCY_NUMBER = "emergency_number"
        const val KEY_TEXT_SIZE = "text_size"
        const val KEY_FAVORITES = "favorites"
    }
}
