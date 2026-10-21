package com.strategytech.simplelauncher

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import kotlin.math.min

/** Big-button dialogs, easier to hit than the standard small Android ones. */
object Dialogs {

    /** Shows [content] in a wide, transparent-framed dialog. The layout supplies its own background. */
    fun showCustom(activity: Activity, content: View): AlertDialog {
        val dialog = AlertDialog.Builder(activity).setView(content).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        val metrics = activity.resources.displayMetrics
        val width = min((metrics.widthPixels * 0.92f).toInt(), (480 * metrics.density).toInt())
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }

    fun confirm(
        activity: Activity,
        title: String,
        yesLabel: String,
        noLabel: String,
        onYes: () -> Unit
    ): AlertDialog {
        val view = activity.layoutInflater.inflate(R.layout.dialog_confirm, null)
        view.findViewById<TextView>(R.id.confirmTitle).text = title
        val yes = view.findViewById<Button>(R.id.confirmYes)
        val no = view.findViewById<Button>(R.id.confirmNo)
        yes.text = yesLabel
        no.text = noLabel
        val dialog = showCustom(activity, view)
        yes.setOnClickListener {
            dialog.dismiss()
            onYes()
        }
        no.setOnClickListener { dialog.dismiss() }
        return dialog
    }
}
