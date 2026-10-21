package com.strategytech.simplelauncher

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher

/** Helpers for becoming (or checking whether we already are) the phone's Home app. */
object HomeRole {

    @Suppress("DEPRECATION")
    fun isDefaultHome(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    private var requestedAt = 0L

    /**
     * Call this from the result callback. If Android refused instantly (the user once chose
     * "don't ask again"), open the Home settings screen so the button never feels dead.
     */
    fun onResult(activity: Activity, result: ActivityResult) {
        val instant = SystemClock.elapsedRealtime() - requestedAt < 600
        if (result.resultCode == Activity.RESULT_CANCELED && instant && !isDefaultHome(activity)) {
            try {
                activity.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                // nothing more we can do
            }
        }
    }

    /** Asks Android to make this app the Home app (system dialog), or opens the Home settings screen. */
    fun request(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        requestedAt = SystemClock.elapsedRealtime()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                launcher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        try {
            launcher.launch(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            launcher.launch(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
