package com.rangia.app

import android.content.Context

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("rangia_prefs", Context.MODE_PRIVATE)

    var treeUri: String?
        get() = prefs.getString("tree_uri", null)
        set(value) = prefs.edit().putString("tree_uri", value).apply()

    var automaticScan: Boolean
        get() = prefs.getBoolean("automatic_scan", true)
        set(value) = prefs.edit().putBoolean("automatic_scan", value).apply()

    var autoMove: Boolean
        get() = prefs.getBoolean("auto_move", false)
        set(value) = prefs.edit().putBoolean("auto_move", value).apply()

    var wholePhoneMode: Boolean
        get() = prefs.getBoolean("whole_phone_mode", true)
        set(value) = prefs.edit().putBoolean("whole_phone_mode", value).apply()
}
