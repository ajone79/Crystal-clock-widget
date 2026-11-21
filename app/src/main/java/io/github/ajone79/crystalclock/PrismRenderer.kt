package io.github.ajone79.crystalclock

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A 3D ring of 12 hexagonal crystal rods laid out like the face of an analog clock.
 *
 * - The rod for the current hour holds liquid that drains (or fills) through the hour.
 * - The whole face turns once a minute around the axis of the lit rod (face-on at :00 and :30).
 * - Seven orbs swirl in the middle. Their sphere grows through the hour, and on
 *   "round" seconds they gather into groups (1 at :00, 2 at :30, 3 at :20/:40, ...).
 */
object PrismRenderer {

    private class V(val x: Float, val y: Float, val z: Float) {
        operator fun plus(o: V) = V(x + o.x, y + o.y, z + o.z)
        operator fun minus(o: V) = V(x - o.x, y - o.y, z - o.z)
        operator fun times(k: Float) = V(x * k, y * k, z * k)
        fun dot(o: V) = x * o.x + y * o.y + z * o.z
        fun cross(o: V) = V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
        fun norm(): V { val l = sqrt(dot(this)); return if (l == 0f) this else this * (1f / l) }
    }

    private class Face(val pts: List<V>, val z: Float, val fill: Int, val edge: Int, val edgeW: Float)

    private val light = V(-0.45f, 0.6f, 0.75f).norm()
    private const val CAM = 4.2f

    // Rod geometry, in units where the ring's outer radius is ~1.
    private const val R0 = 0.42f
    private const val R1 = 0.88f
    private const val TIP = 0.11f
    private const val A = 0.065f

    /**
     * @param secondOfMinute 0..60 with fraction (used for the turn and orb grouping when [animate])
     * @param minuteOfHour 0..60 with fraction
     * @param tSec free-running time in seconds, for spin and orb motion
     * @param fullBackground draw the deep "tunnel" background over the whole canvas (wallpaper)
     * @param backdrop draw a round dark disc behind the clock (widget)
     */
    fun draw(
        c: Canvas, w: Int, h: Int,
        secondOfMinute: Float, minuteOfHour: Float, hour24: Int,
        mode: String, paletteKey: String,
        tSec: Float, animate: Boolean,
        fullBackground: Boolean, backdrop: Boolean,
    ) {
        val pal = CrystalRenderer.palettes[paletteKey] ?: CrystalRenderer.palettes.getValue("aqua")
        val cx = w / 2f
        val cy = h / 2f
        val minSide = min(w, h).toFloat()
        val scale = minSide * 0.47f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        if (fullBackground) drawTunnel(c, w, h, tSec, pal, p)
        if (backdrop) {
            p.shader = RadialGradient(
                cx, cy, minSide / 2f,
                intArrayOf(Color.argb(225, 14, 20, 38), Color.argb(235, 4, 6, 14)),
                floatArrayOf(0.35f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawCircle(cx, cy, minSide / 2f, p)
            p.shader = null
        }

        val drain = when (mode) {
            "fill" -> false
            "alternate" -> hour24 % 2 == 1
            else -> true
        }
        val minuteFrac = (minuteOfHour / 60f).coerceIn(0f, 1f)
        val level = if (drain) 1f - minuteFrac else minuteFrac
        val hourIdx = hour24 % 12

        // Face rotation: around the lit rod's axis. One full turn per minute.
        val hourDir = radial(hourIdx)
        val turn = if (animate) (secondOfMinute / 60f) * 2f * PI.toFloat() else 0.62f
        val tilt = if (animate) 0.18f else 0f // a slight constant lean so it never looks flat
        fun place(v: V): V {
            val r = rotate(v, hourDir, turn)
            return rotate(r, V(1f, 0f, 0f), tilt)
        }

        val faces = ArrayList<Face>(200)
        val cam = V(0f, 0f, CAM)

        for (i in 0 until 12) {
            val lit = i == hourIdx
            val u = radial(i)
            val vIn = V(-u.y, u.x, 0f)
            val nz = V(0f, 0f, 1f)
            val spin = if (animate) tSec * 0.5f + i * 0.9f else i * 0.55f

            fun ring(r: Float, a: Float): List<V> = List(6) { k ->
                val ang = spin + k * (PI.toFloat() / 3f)
                u * r + vIn * (a * cos(ang)) + nz * (a * sin(ang))
            }

            val inner = ring(R0, A).map(::place)
            val outer = ring(R1, A).map(::place)
            val apex = place(u * (R1 + TIP))
            val centre = place(u * ((R0 + R1) / 2f))

            val glassRGB = if (lit) pal.bright else 0xFFCFEFFF.toInt()
            fun addFace(pts: List<V>, liquid: Boolean = false) {
                val fc = centroid(pts)
                var n = (pts[1] - pts[0]).cross(pts[2] - pts[0]).norm()
                if (n.dot(fc - centre) < 0f) n = n * -1f
                val front = n.dot(cam - fc) > 0f
                val shade = 0.35f + 0.65f * max(0f, n.dot(light))
                val fill: Int
                val edge: Int
                if (liquid) {
                    val base = lerpColor(pal.mid, pal.bright, shade)
                    fill = withAlpha(base, if (front) 235 else 170)
                    edge = withAlpha(pal.bright, if (front) 160 else 60)
                } else {
                    fill = withAlpha(lerpColor(0xFF1A2A44.toInt(), glassRGB, shade), ((if (front) 34 else 16) + (if (lit) 18 else 0)))
                    edge = withAlpha(glassRGB, if (front) (if (lit) 220 else 140) else 45)
                }
                faces.add(Face(pts, fc.z, fill, edge, if (front) 1.3f else 0.8f))
            }

            // Glass rod: 6 sides, inner cap, pointed tip.
            for (k in 0 until 6) {
                val k2 = (k + 1) % 6
                addFace(listOf(inner[k], inner[k2], outer[k2], outer[k]))
                addFace(listOf(outer[k], outer[k2], apex))
            }
            addFace(inner.reversed())

            // Liquid inside the lit rod.
            if (lit && level > 0.001f) {
                val top = R0 + 0.01f + (R1 - R0 - 0.02f) * level
                val li = ring(R0 + 0.01f, A * 0.8f).map(::place)
                val lo = ring(top, A * 0.8f).map(::place)
                for (k in 0 until 6) {
                    val k2 = (k + 1) % 6
                    addFace(listOf(li[k], li[k2], lo[k2], lo[k]), liquid = true)
                }
                addFace(lo, liquid = true)
            }
        }

        // Orbs.
        val orbs = orbPositions(secondOfMinute, minuteFrac, tSec, animate, hourDir).map(::place)

        // Paint back to front.
        faces.sortBy { it.z }
        val path = Path()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
        val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
        val unit = minSide / 512f

        fun proj(v: V): Pair<Float, Float> {
            val d = CAM - v.z
            return Pair(cx + scale * v.x * CAM / d, cy - scale * v.y * CAM / d)
        }

        // Interleave orbs by depth.
        val orbSorted = orbs.sortedBy { it.z }
        var oi = 0
        fun drawOrb(o: V) {
            val (x, y) = proj(o)
            val d = CAM - o.z
            val r = 10f * unit * CAM / d
            fillP.shader = RadialGradient(
                x, y, r * 2.2f,
                intArrayOf(Color.WHITE, withAlpha(pal.glow, 170), withAlpha(pal.glow, 0)),
                floatArrayOf(0f, 0.28f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawCircle(x, y, r * 2.2f, fillP)
            fillP.shader = null
        }

        for (face in faces) {
            while (oi < orbSorted.size && orbSorted[oi].z < face.z) drawOrb(orbSorted[oi++])
            path.reset()
            face.pts.forEachIndexed { idx, v ->
                val (x, y) = proj(v)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            fillP.color = face.fill
            c.drawPath(path, fillP)
            stroke.color = face.edge
            stroke.strokeWidth = face.edgeW * unit * 1.4f
            c.drawPath(path, stroke)
        }
        while (oi < orbSorted.size) drawOrb(orbSorted[oi++])

        // Soft glow around the lit rod.
        val litC = place(hourDir * ((R0 + R1) / 2f))
        val (gx, gy) = proj(litC)
        fillP.shader = RadialGradient(
            gx, gy, 120f * unit,
            intArrayOf(withAlpha(pal.glow, (40 + 60 * level).toInt()), withAlpha(pal.glow, 0)),
            null, Shader.TileMode.CLAMP,
        )
        c.drawCircle(gx, gy, 120f * unit, fillP)
        fillP.shader = null
    }

    /** How many groups the 7 orbs gather into on a given whole second (7 = no grouping). */
    fun groupsFor(sec: Int): Int = when {
        sec % 60 == 0 -> 1
        sec % 30 == 0 -> 2
        sec % 20 == 0 -> 3
        sec % 15 == 0 -> 4
        sec % 12 == 0 -> 5
        sec % 10 == 0 -> 6
        else -> 7
    }

    private fun orbPositions(sec: Float, minuteFrac: Float, t: Float, animate: Boolean, hourDir: V): List<V> {
        val rho = 0.06f + 0.26f * minuteFrac
        val tt = if (animate) t else minuteFrac * 400f
        val base = List(7) { j ->
            val a = tt * (0.9f + 0.17f * j) + j * 0.9f
            val b = tt * (0.55f + 0.11f * j) + j * 2.1f
            V(cos(a) * sin(b), sin(a) * sin(b), cos(b)) * rho
        }
        if (!animate) return base
        val n = sec.roundToInt()
        val g = groupsFor(n)
        if (g >= 7) return base
        val d = abs(sec - n)
        val k = smooth(1f - (d / 0.7f).coerceIn(0f, 1f))
        return base.mapIndexed { j, v ->
            val target = if (g == 1) hourDir * rho else base[j % g]
            v + (target - v) * k
        }
    }

    private fun drawTunnel(c: Canvas, w: Int, h: Int, t: Float, pal: CrystalRenderer.Palette, p: Paint) {
        val cx = w / 2f
        val cy = h / 2f
        val maxR = sqrt(cx * cx + cy * cy)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFF05070F.toInt(), 0xFF0B1328.toInt(), 0xFF04060C.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = RadialGradient(cx, cy, maxR * 0.7f,
            intArrayOf(withAlpha(pal.deep, 120), withAlpha(pal.deep, 0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, maxR, p)
        p.shader = null
        // Rings drifting outward: the sense of slowly falling down a tunnel.
        p.style = Paint.Style.STROKE
        val count = 9
        for (k in 0 until count) {
            val q = ((k + t * 0.06f) % count) / count
            val r = q * q * maxR * 1.1f
            p.strokeWidth = 1f + q * 3f
            p.color = withAlpha(pal.glow, (q * (1f - q) * 4f * 38f).toInt())
            c.drawCircle(cx, cy, r, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun radial(i: Int): V {
        val a = (PI / 2 - i * PI / 6).toFloat()
        return V(cos(a), sin(a), 0f)
    }

    private fun rotate(v: V, axis: V, ang: Float): V {
        val k = axis.norm()
        val c = cos(ang)
        val s = sin(ang)
        return v * c + k.cross(v) * s + k * (k.dot(v) * (1 - c))
    }

    private fun centroid(pts: List<V>): V {
        var x = 0f; var y = 0f; var z = 0f
        for (q in pts) { x += q.x; y += q.y; z += q.z }
        val n = pts.size.toFloat()
        return V(x / n, y / n, z / n)
    }

    private fun smooth(x: Float) = x * x * (3 - 2 * x)

    private fun withAlpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt(),
        )
    }
}
