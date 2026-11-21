package io.github.ajone79.crystalclock

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("crystal_clock", Context.MODE_PRIVATE)

    /** "prism" (3D rods, PS2-menu feel) or "gems" (flat ring of 12 gems). */
    var style: String
        get() = sp.getString("style", "prism") ?: "prism"
        set(v) = sp.edit().putString("style", v).apply()

    var mode: String
        get() = sp.getString("mode", "drain") ?: "drain"
        set(v) = sp.edit().putString("mode", v).apply()

    var palette: String
        get() = sp.getString("palette", "aqua") ?: "aqua"
        set(v) = sp.edit().putString("palette", v).apply()

    var backdrop: Boolean
        get() = sp.getBoolean("backdrop", true)
        set(v) = sp.edit().putBoolean("backdrop", v).apply()

    var showTime: Boolean
        get() = sp.getBoolean("show_time", true)
        set(v) = sp.edit().putBoolean("show_time", v).apply()
}
