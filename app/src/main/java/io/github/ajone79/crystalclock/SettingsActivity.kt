package io.github.ajone79.crystalclock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var preview: ImageView
    private val handler = Handler(Looper.getMainLooper())
    private var demoStep = -1

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

        preview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (260 * dp).toInt())
        }
        col.addView(preview)

        col.addView(Button(this).apply {
            text = "Play a demo hour"
            setOnClickListener { startDemo() }
        })

        col.addView(label("Crystals"))
        col.addView(radioGroup(
            listOf("fill" to "Fill up through the hour", "drain" to "Drain through the hour", "alternate" to "Fill one hour, drain the next"),
            prefs.mode,
        ) { prefs.mode = it })

        col.addView(label("Colour"))
        col.addView(radioGroup(
            CrystalRenderer.palettes.keys.map { it to it.replaceFirstChar(Char::uppercase) },
            prefs.palette,
        ) { prefs.palette = it })

        col.addView(Switch(this).apply {
            text = "Dark background behind the ring"
            isChecked = prefs.backdrop
            setTextColor(Color.WHITE)
            setPadding(0, (20 * dp).toInt(), 0, 0)
            setOnCheckedChangeListener { _, on -> prefs.backdrop = on; changed() }
        })

        col.addView(Button(this).apply {
            text = "Add widget to home screen"
            setOnClickListener { pinWidget() }
        })

        col.addView(label("Or: long-press your home screen, tap Widgets, find Crystal Clock, and drag it out. Tap the widget any time to come back here."))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(10, 12, 20))
            addView(col)
        })
        drawPreview()
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
        drawPreview()
        CrystalClockWidget.updateAll(this)
    }

    private fun drawPreview(minute: Float? = null, hour: Int? = null) {
        val now = Calendar.getInstance()
        val m = minute ?: (now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f)
        val h = hour ?: now.get(Calendar.HOUR_OF_DAY)
        preview.setImageBitmap(CrystalRenderer.render(720, m, h, prefs.mode, prefs.palette, prefs.backdrop))
    }

    /** Runs two hours in about 8 seconds so you can see fill and drain. */
    private fun startDemo() {
        handler.removeCallbacksAndMessages(null)
        demoStep = 0
        val tick = object : Runnable {
            override fun run() {
                if (demoStep > 240) { demoStep = -1; drawPreview(); return }
                drawPreview(minute = (demoStep % 120) / 2f, hour = demoStep / 120)
                demoStep++
                handler.postDelayed(this, 33)
            }
        }
        handler.post(tick)
    }

    private fun pinWidget() {
        val mgr = getSystemService(AppWidgetManager::class.java)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, CrystalClockWidget::class.java), null, null)
        } else {
            Toast.makeText(this, "Your launcher can't do this. Long-press the home screen and pick Widgets.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }
}
