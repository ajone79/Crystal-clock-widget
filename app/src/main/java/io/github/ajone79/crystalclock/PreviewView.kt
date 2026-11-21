package io.github.ajone79.crystalclock

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import java.util.Calendar

/** Live, animated preview in the settings screen. */
class PreviewView(context: Context) : View(context) {
    private val prefs = Prefs(context)
    private val start = SystemClock.elapsedRealtime()

    /** When set, plays a fast "demo hour" instead of the real time. */
    var demoStartedAt = -1L

    override fun onDraw(canvas: Canvas) {
        val now = Calendar.getInstance()
        var sec = now.get(Calendar.SECOND) + now.get(Calendar.MILLISECOND) / 1000f
        var minute = now.get(Calendar.MINUTE) + sec / 60f
        var hour = now.get(Calendar.HOUR_OF_DAY)
        val t = (SystemClock.elapsedRealtime() - start) / 1000f

        if (demoStartedAt >= 0) {
            // Two hours in 10 seconds.
            val d = (SystemClock.elapsedRealtime() - demoStartedAt) / 10_000f
            if (d >= 1f) demoStartedAt = -1 else {
                val total = d * 120f
                hour = (hour + total.toInt() / 60) % 24
                minute = total % 60f
                sec = (minute * 60f) % 60f
            }
        }

        if (prefs.style == "gems") {
            val side = minOf(width, height)
            val bmp = CrystalRenderer.render(side, minute, hour, prefs.mode, prefs.palette, prefs.backdrop)
            canvas.drawBitmap(bmp, (width - side) / 2f, (height - side) / 2f, null)
            bmp.recycle()
        } else {
            PrismRenderer.draw(
                canvas, width, height,
                secondOfMinute = sec, minuteOfHour = minute, hour24 = hour,
                mode = prefs.mode, paletteKey = prefs.palette,
                tSec = t, animate = true,
                fullBackground = true, backdrop = false,
            )
        }
        postInvalidateOnAnimation()
    }
}
