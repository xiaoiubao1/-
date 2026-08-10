package com.xiaoiubao.suixinji.settings

import android.content.Context

enum class ThemePreset(val title: String) {
    CREAM("奶油橙"),
    SAKURA("樱花粉"),
    SKY("天空蓝"),
    MINT("薄荷绿"),
    DARK("深色")
}

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("suixinji_settings", Context.MODE_PRIVATE)

    var theme: ThemePreset
        get() = runCatching { ThemePreset.valueOf(prefs.getString(KEY_THEME, null) ?: ThemePreset.CREAM.name) }
            .getOrDefault(ThemePreset.CREAM)
        set(value) { prefs.edit().putString(KEY_THEME, value.name).apply() }

    var wallpaper: String
        get() = prefs.getString(KEY_WALLPAPER, WALLPAPER_BUILTIN).orEmpty()
        set(value) { prefs.edit().putString(KEY_WALLPAPER, value).apply() }

    companion object {
        const val WALLPAPER_NONE = "none"
        const val WALLPAPER_BUILTIN = "builtin"
        private const val KEY_THEME = "theme"
        private const val KEY_WALLPAPER = "wallpaper"
    }
}
