package com.strategytech.simplelauncher

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Keeps only the characters a phone dialer understands. */
fun String.dialable(): String = filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

/**
 * Apps targeting Android 15+ draw edge-to-edge. This adds the status bar, navigation bar,
 * camera cutout and keyboard heights to this view's own padding so nothing is hidden behind them.
 */
fun View.applySystemBarPadding() {
    val baseLeft = paddingLeft
    val baseTop = paddingTop
    val baseRight = paddingRight
    val baseBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val types = WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.ime()
        val bars = insets.getInsets(types)
        v.setPadding(
            baseLeft + bars.left,
            baseTop + bars.top,
            baseRight + bars.right,
            baseBottom + bars.bottom
        )
        WindowInsetsCompat.CONSUMED
    }
    ViewCompat.requestApplyInsets(this)
}
