package io.github.ajone79.crystalclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar

class CrystalClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
        scheduleNext(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        updateAll(context)
    }

    override fun onEnabled(context: Context) = scheduleNext(context)

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(tickIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                updateAll(context)
                scheduleNext(context)
            }
        }
    }

    companion object {
        private const val ACTION_TICK = "io.github.ajone79.crystalclock.TICK"
        private const val BITMAP_PX = 512

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CrystalClockWidget::class.java))
            if (ids.isEmpty()) return

            val prefs = Prefs(context)
            val now = Calendar.getInstance()
            val minute = now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f
            val bmp = renderFrame(prefs, BITMAP_PX, minute, now.get(Calendar.HOUR_OF_DAY))
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, SettingsActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            for (id in ids) {
                val opts = manager.getAppWidgetOptions(id)
                val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
                val side = minOf(wDp, hDp).coerceAtLeast(80).toFloat()

                val views = RemoteViews(context.packageName, R.layout.widget_crystal_clock)
                views.setImageViewBitmap(R.id.ring, bmp)
                views.setTextViewTextSize(R.id.time, TypedValue.COMPLEX_UNIT_DIP, side * 0.17f)
                views.setTextViewTextSize(R.id.date, TypedValue.COMPLEX_UNIT_DIP, side * 0.058f)
                views.setViewVisibility(R.id.labels, if (prefs.showTime) View.VISIBLE else View.GONE)
                views.setOnClickPendingIntent(R.id.root, open)
                manager.updateAppWidget(id, views)
            }
        }

        /** One still frame of the chosen style, used by the widget and the settings preview. */
        fun renderFrame(prefs: Prefs, sizePx: Int, minute: Float, hour24: Int): Bitmap {
            if (prefs.style == "gems") {
                return CrystalRenderer.render(sizePx, minute, hour24, prefs.mode, prefs.palette, prefs.backdrop)
            }
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            PrismRenderer.draw(
                Canvas(bmp), sizePx, sizePx,
                secondOfMinute = 0f, minuteOfHour = minute, hour24 = hour24,
                mode = prefs.mode, paletteKey = prefs.palette,
                tSec = 0f, animate = false,
                fullBackground = false, backdrop = prefs.backdrop,
            )
            return bmp
        }

        private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, CrystalClockWidget::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Wake at the start of the next minute (non-wakeup: no battery drain while the screen is off). */
        fun scheduleNext(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java)
            val next = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 200)
                add(Calendar.MINUTE, 1)
            }.timeInMillis
            val pi = tickIntent(context)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExact(AlarmManager.RTC, next, pi)
            } else {
                am.setWindow(AlarmManager.RTC, next, 15_000L, pi)
            }
        }
    }
}
