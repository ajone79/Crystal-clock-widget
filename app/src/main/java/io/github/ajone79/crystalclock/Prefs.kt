package io.github.ajone79.crystalclock

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("crystal_clock", Context.MODE_PRIVATE)

    var mode: String
        get() = sp.getString("mode", "fill") ?: "fill"
        set(v) = sp.edit().putString("mode", v).apply()

    var palette: String
        get() = sp.getString("palette", "aqua") ?: "aqua"
        set(v) = sp.edit().putString("palette", v).apply()

    var backdrop: Boolean
        get() = sp.getBoolean("backdrop", true)
        set(v) = sp.edit().putBoolean("backdrop", v).apply()
}
