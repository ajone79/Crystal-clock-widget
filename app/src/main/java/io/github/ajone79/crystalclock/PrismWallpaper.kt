package io.github.ajone79.crystalclock

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.util.Calendar

/** Live wallpaper: the full animated crystal clock behind your home screen. */
class PrismWallpaper : WallpaperService() {

    override fun onCreateEngine(): Engine = ClockEngine()

    inner class ClockEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var width = 0
        private var height = 0
        private val start = SystemClock.elapsedRealtime()
        private val frame = Runnable { drawFrame() }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) drawFrame() else handler.removeCallbacks(frame)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            this.width = width
            this.height = height
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            super.onDestroy()
        }

        private fun drawFrame() {
            handler.removeCallbacks(frame)
            if (width == 0 || height == 0) return
            val holder = surfaceHolder
            val canvas = try { holder.lockHardwareCanvas() } catch (e: Exception) { null } ?: return
            try {
                val prefs = Prefs(this@PrismWallpaper)
                val now = Calendar.getInstance()
                val sec = now.get(Calendar.SECOND) + now.get(Calendar.MILLISECOND) / 1000f
                val minute = now.get(Calendar.MINUTE) + sec / 60f
                PrismRenderer.draw(
                    canvas, width, height,
                    secondOfMinute = sec, minuteOfHour = minute, hour24 = now.get(Calendar.HOUR_OF_DAY),
                    mode = prefs.mode, paletteKey = prefs.palette,
                    tSec = (SystemClock.elapsedRealtime() - start) / 1000f, animate = true,
                    fullBackground = true, backdrop = false,
                )
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            if (visible) handler.postDelayed(frame, 33)
        }
    }
}
