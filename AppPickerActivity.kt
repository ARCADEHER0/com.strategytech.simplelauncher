package com.strategytech.simplelauncher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Lists every app on the phone so a family member can pick one for the "My Apps" tray. */
class AppPickerActivity : BaseActivity() {

    private class AppEntry(
        val component: ComponentName,
        val label: String,
        val resolve: ResolveInfo
    )

    private lateinit var prefs: Prefs
    private lateinit var searchInput: EditText
    private lateinit var listView: ListView
    private val adapter = AppAdapter()
    private var allApps: List<AppEntry> = emptyList()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_app_picker)
        findViewById<View>(R.id.root).applySystemBarPadding()

        searchInput = findViewById(R.id.searchInput)
        listView = findViewById(R.id.appList)
        listView.adapter = adapter
        listView.emptyView = findViewById(R.id.emptyText)

        listView.setOnItemClickListener { _, _, position, _ ->
            val entry = adapter.items[position]
            prefs.addFavorite(entry.component.flattenToString())
            Toast.makeText(this, getString(R.string.added_fmt, entry.label), Toast.LENGTH_SHORT).show()
            finish()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s?.toString().orEmpty())
            }
        })

        findViewById<Button>(R.id.cancelButton).setOnClickListener { finish() }

        loadApps()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun loadApps() {
        val pm = packageManager
        val alreadyAdded = prefs.favorites().toSet()
        val ownPackage = packageName
        executor.execute {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val entries = queryLaunchables(pm, launcherIntent)
                .mapNotNull { resolveInfo ->
                    val info = resolveInfo.activityInfo ?: return@mapNotNull null
                    if (info.packageName == ownPackage) return@mapNotNull null
                    val component = ComponentName(info.packageName, info.name)
                    if (component.flattenToString() in alreadyAdded) return@mapNotNull null
                    AppEntry(component, resolveInfo.loadLabel(pm).toString(), resolveInfo)
                }
                .sortedBy { it.label.lowercase() }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                allApps = entries
                findViewById<TextView>(R.id.emptyText).setText(R.string.picker_empty)
                applyFilter(searchInput.text?.toString().orEmpty())
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun queryLaunchables(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(intent, 0)
        }

    private fun applyFilter(query: String) {
        val q = query.trim()
        adapter.items = if (q.isEmpty()) allApps else allApps.filter { it.label.contains(q, ignoreCase = true) }
    }

    private inner class AppAdapter : BaseAdapter() {
        var items: List<AppEntry> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): Any = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false)
            val entry = items[position]
            view.findViewById<ImageView>(R.id.appIcon).setImageDrawable(entry.resolve.loadIcon(packageManager))
            view.findViewById<TextView>(R.id.appLabel).text = entry.label
            return view
        }
    }
}
