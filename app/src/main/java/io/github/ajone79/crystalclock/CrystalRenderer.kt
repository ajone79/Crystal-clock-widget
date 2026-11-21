package io.github.ajone79.crystalclock

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader

/** Draws the ring of 12 crystals. Each crystal is 5 minutes of the hour. */
object CrystalRenderer {

    class Palette(val deep: Int, val mid: Int, val bright: Int, val glow: Int)

    val palettes: Map<String, Palette> = linkedMapOf(
        "aqua" to Palette(0xFF0A3D7A.toInt(), 0xFF1E90D6.toInt(), 0xFF8FF3FF.toInt(), 0xFF4FD8FF.toInt()),
        "amethyst" to Palette(0xFF3B0F6B.toInt(), 0xFF8A3FD1.toInt(), 0xFFE6B8FF.toInt(), 0xFFB46CFF.toInt()),
        "ruby" to Palette(0xFF5A0718.toInt(), 0xFFD11A44.toInt(), 0xFFFFB3C2.toInt(), 0xFFFF4D6D.toInt()),
        "emerald" to Palette(0xFF06402B.toInt(), 0xFF17A86A.toInt(), 0xFFB6FFD9.toInt(), 0xFF3DFF9E.toInt()),
        "amber" to Palette(0xFF5A2E00.toInt(), 0xFFE08A0B.toInt(), 0xFFFFE7A3.toInt(), 0xFFFFB627.toInt()),
    )

    /**
     * Fill level (0..1) of each crystal.
     * minuteOfHour can carry a fraction (e.g. 12.5 = 12m30s).
     * mode: "fill", "drain", or "alternate" (fill on even hours, drain on odd hours).
     */
    fun levels(minuteOfHour: Float, hour24: Int, mode: String): FloatArray {
        val drain = when (mode) {
            "drain" -> true
            "alternate" -> hour24 % 2 == 1
            else -> false
        }
        return FloatArray(12) { i ->
            val f = ((minuteOfHour - 5f * i) / 5f).coerceIn(0f, 1f)
            if (drain) 1f - f else f
        }
    }

    fun render(
        sizePx: Int,
        minuteOfHour: Float,
        hour24: Int,
        mode: String,
        paletteKey: String,
        backdrop: Boolean,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pal = palettes[paletteKey] ?: palettes.getValue("aqua")
        val lv = levels(minuteOfHour, hour24, mode)

        val s = sizePx / 512f
        val cx = sizePx / 2f
        val rIn = 126f * s
        val rOut = 234f * s
        val len = rOut - rIn
        val rA = rIn + 0.2f * len
        val rB = rIn + 0.64f * len
        val w = 23f * s

        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Backdrop disc so the ring reads on any wallpaper.
        if (backdrop) {
            p.shader = RadialGradient(
                cx, cx, 254f * s,
                intArrayOf(Color.argb(215, 22, 28, 44), Color.argb(225, 8, 10, 18)),
                floatArrayOf(0.5f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawCircle(cx, cx, 254f * s, p)
            p.shader = null
        }
        // Faint inner guide ring.
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * s
        p.color = Color.argb(40, 255, 255, 255)
        c.drawCircle(cx, cx, rIn - 8f * s, p)
        p.style = Paint.Style.FILL

        // Crystal outline in local coordinates: pointing "up" (-y) from the centre.
        val shape = Path().apply {
            moveTo(0f, -rIn)
            lineTo(w, -rA)
            lineTo(w * 0.9f, -rB)
            lineTo(0f, -rOut)
            lineTo(-w * 0.9f, -rB)
            lineTo(-w, -rA)
            close()
        }
        val leftFacet = Path().apply {
            moveTo(0f, -rIn)
            lineTo(-w, -rA)
            lineTo(-w * 0.9f, -rB)
            lineTo(0f, -rOut)
            close()
        }

        // Pass 1: glow behind lit crystals.
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(16f * s, BlurMaskFilter.Blur.NORMAL)
        }
        for (i in 0 until 12) {
            if (lv[i] <= 0f) continue
            c.save()
            c.translate(cx, cx)
            c.rotate(i * 30f)
            glow.color = withAlpha(pal.glow, (150 * lv[i]).toInt())
            c.drawPath(shape, glow)
            c.restore()
        }

        // Pass 2: crystals.
        val liquid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, -rIn, 0f, -rOut,
                intArrayOf(pal.deep, pal.mid, pal.bright),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
        }
        for (i in 0 until 12) {
            val level = lv[i]
            c.save()
            c.translate(cx, cx)
            c.rotate(i * 30f)

            // Empty glass.
            p.color = withAlpha(blend(pal.deep, Color.BLACK, 0.55f), 170)
            c.drawPath(shape, p)

            c.save()
            c.clipPath(shape)
            if (level > 0f) {
                val top = -rIn - level * len
                c.drawRect(-w - 2f, top, w + 2f, -rIn + 2f, liquid)
                if (level < 1f) {
                    // Bright liquid surface line.
                    p.color = pal.bright
                    c.drawRect(-w - 2f, top - 1.2f * s, w + 2f, top + 1.6f * s, p)
                }
            }
            // Facets: lit left half, shaded right half, centre ridge.
            p.color = Color.argb(34, 255, 255, 255)
            c.drawPath(leftFacet, p)
            p.color = Color.argb(30, 0, 0, 0)
            c.drawRect(0f, -rOut, w + 2f, -rIn, p)
            stroke.strokeWidth = 1.2f * s
            stroke.color = Color.argb(70, 255, 255, 255)
            c.drawLine(0f, -rIn, 0f, -rOut, stroke)
            c.drawLine(-w, -rA, 0f, -rA - 10f * s, stroke)
            c.drawLine(w, -rA, 0f, -rA - 10f * s, stroke)
            c.restore()

            // Outline.
            stroke.strokeWidth = 1.8f * s
            stroke.color = if (level > 0f) withAlpha(pal.bright, 210) else Color.argb(80, 255, 255, 255)
            c.drawPath(shape, stroke)

            // Sparkle on full crystals.
            if (level >= 1f) drawSparkle(c, -w * 0.38f, -rB + 4f * s, 7f * s, p)

            c.restore()
        }

        // Hour marker: a small diamond just outside the ring.
        c.save()
        c.translate(cx, cx)
        c.rotate((hour24 % 12) * 30f)
        val d = 6f * s
        val y = -rOut - 13f * s
        p.color = Color.WHITE
        c.drawPath(Path().apply {
            moveTo(0f, y - d); lineTo(d * 0.75f, y); lineTo(0f, y + d); lineTo(-d * 0.75f, y); close()
        }, p)
        c.restore()

        return bmp
    }

    private fun drawSparkle(c: Canvas, x: Float, y: Float, r: Float, p: Paint) {
        p.color = Color.argb(230, 255, 255, 255)
        val t = r * 0.18f
        c.drawPath(Path().apply {
            moveTo(x, y - r); lineTo(x + t, y - t); lineTo(x + r, y); lineTo(x + t, y + t)
            lineTo(x, y + r); lineTo(x - t, y + t); lineTo(x - r, y); lineTo(x - t, y - t); close()
        }, p)
    }

    private fun withAlpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt(),
    )
}
