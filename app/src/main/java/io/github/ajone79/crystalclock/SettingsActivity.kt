package io.github.ajone79.crystalclock

import android.app.Activity
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var preview: PreviewView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val dp = resources.displayMetrics.density

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (32 * dp).toInt(), (20 * dp).toInt(), (32 * dp).toInt())
        }
        fun label(text: String, big: Boolean = false) = TextView(this).apply {
            this.text = text
            textSize = if (big) 24f else 15f
            setTextColor(if (big) Color.WHITE else Color.argb(200, 255, 255, 255))
            setPadding(0, (if (big) 0 else 20 * dp).toInt(), 0, (6 * dp).toInt())
        }

        col.addView(label("Crystal Clock", big = true))

        preview = PreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (340 * dp).toInt())
        }
        col.addView(preview)

        col.addView(Button(this).apply {
            text = "Play a demo (2 hours in 10 seconds)"
            setOnClickListener { preview.demoStartedAt = SystemClock.elapsedRealtime() }
        })

        col.addView(Button(this).apply {
            text = "Set as live wallpaper"
            setOnClickListener { setWallpaper() }
        })

        col.addView(Button(this).apply {
            text = "Add widget to home screen"
            setOnClickListener { pinWidget() }
        })

        col.addView(label("Style"))
        col.addView(radioGroup(
            listOf("prism" to "Crystal rods (3D, like the PS2 menu)", "gems" to "Gem ring (one gem per 5 minutes)"),
            prefs.style,
        ) { prefs.style = it })

        col.addView(label("Liquid"))
        col.addView(radioGroup(
            listOf("drain" to "Drain through the hour", "fill" to "Fill up through the hour", "alternate" to "Fill one hour, drain the next"),
            prefs.mode,
        ) { prefs.mode = it })

        col.addView(label("Colour"))
        col.addView(radioGroup(
            CrystalRenderer.palettes.keys.map { it to it.replaceFirstChar(Char::uppercase) },
            prefs.palette,
        ) { prefs.palette = it })

        col.addView(Switch(this).apply {
            text = "Dark background behind the widget"
            isChecked = prefs.backdrop
            setTextColor(Color.WHITE)
            setPadding(0, (20 * dp).toInt(), 0, 0)
            setOnCheckedChangeListener { _, on -> prefs.backdrop = on; changed() }
        })

        col.addView(Switch(this).apply {
            text = "Show the time and date on the widget"
            isChecked = prefs.showTime
            setTextColor(Color.WHITE)
            setPadding(0, (16 * dp).toInt(), 0, 0)
            setOnCheckedChangeListener { _, on -> prefs.showTime = on; changed() }
        })

        col.addView(label("The live wallpaper is the full animated clock: the ring turns once a minute and seven orbs swirl in the middle. The widget shows a still frame that updates every minute. Tap the widget to come back here."))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(6, 8, 16))
            addView(col)
        })
    }

    private fun radioGroup(options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit): RadioGroup {
        val group = RadioGroup(this)
        options.forEach { (key, title) ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = title
                tag = key
                setTextColor(Color.WHITE)
                isChecked = key == current
            })
        }
        group.setOnCheckedChangeListener { g, checkedId ->
            onPick(g.findViewById<View>(checkedId).tag as String)
            changed()
        }
        return group
    }

    private fun changed() {
        preview.invalidate()
        CrystalClockWidget.updateAll(this)
    }

    private fun setWallpaper() {
        val component = ComponentName(this, PrismWallpaper::class.java)
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        try {
            startActivity(direct)
        } catch (e: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (e2: Exception) {
                Toast.makeText(this, "Long-press your home screen, tap Wallpapers, and pick Crystal Clock.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun pinWidget() {
        val mgr = getSystemService(AppWidgetManager::class.java)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, CrystalClockWidget::class.java), null, null)
        } else {
            Toast.makeText(this, "Long-press the home screen and pick Widgets.", Toast.LENGTH_LONG).show()
        }
    }
}
