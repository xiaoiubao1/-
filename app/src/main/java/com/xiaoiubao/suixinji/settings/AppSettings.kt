package com.xiaoiubao.suixinji.settings

import android.content.Context

enum class ThemePreset(val title: String) {
    CREAM("蓝色"),
    SAKURA("紫色"),
    SKY("青色"),
    MINT("粉色"),
    DARK("橙色")
}

enum class BackgroundStyle(val title: String) {
    LIGHT("浅色"),
    GRAY("灰色")
}

enum class IconStyle(val title: String, val componentName: String) {
    CALENDAR("日历", "LauncherCalendar"),
    CHECK("清单", "LauncherCheck"),
    GRID("方格", "LauncherGrid"),
    NOTE("便签", "LauncherNote")
}

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("suixinji_settings", Context.MODE_PRIVATE)

    var theme: ThemePreset
        get() = runCatching {
            ThemePreset.valueOf(prefs.getString(KEY_THEME, null) ?: ThemePreset.CREAM.name)
        }.getOrDefault(ThemePreset.CREAM)
        set(value) { prefs.edit().putString(KEY_THEME, value.name).apply() }

    var backgroundStyle: BackgroundStyle
        get() = runCatching {
            BackgroundStyle.valueOf(
                prefs.getString(KEY_BACKGROUND_STYLE, null) ?: BackgroundStyle.LIGHT.name
            )
        }.getOrDefault(BackgroundStyle.LIGHT)
        set(value) { prefs.edit().putString(KEY_BACKGROUND_STYLE, value.name).apply() }

    var customBackgroundEnabled: Boolean
        get() = prefs.getBoolean(KEY_CUSTOM_BACKGROUND_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_CUSTOM_BACKGROUND_ENABLED, value).apply() }

    var wallpaper: String
        get() = prefs.getString(KEY_WALLPAPER, "")
            .orEmpty()
            .takeUnless { it == WALLPAPER_NONE || it == WALLPAPER_BUILTIN }
            .orEmpty()
        set(value) {
            val normalized = value.takeUnless { it == WALLPAPER_NONE || it == WALLPAPER_BUILTIN }.orEmpty()
            prefs.edit().putString(KEY_WALLPAPER, normalized).apply()
        }

    var glassStrength: Float
        get() = prefs.getFloat(KEY_GLASS_STRENGTH, 0.60f).coerceIn(0f, 1f)
        set(value) { prefs.edit().putFloat(KEY_GLASS_STRENGTH, value.coerceIn(0f, 1f)).apply() }

    var iconStyle: IconStyle
        get() = runCatching {
            IconStyle.valueOf(prefs.getString(KEY_ICON_STYLE, null) ?: IconStyle.CALENDAR.name)
        }.getOrDefault(IconStyle.CALENDAR)
        set(value) { prefs.edit().putString(KEY_ICON_STYLE, value.name).apply() }

    companion object {
        // 仅保留用于兼容旧备份；新版不再提供内置壁纸。
        const val WALLPAPER_NONE = "none"
        const val WALLPAPER_BUILTIN = "builtin"

        private const val KEY_THEME = "theme"
        private const val KEY_BACKGROUND_STYLE = "background_style"
        private const val KEY_CUSTOM_BACKGROUND_ENABLED = "custom_background_enabled"
        private const val KEY_WALLPAPER = "wallpaper"
        private const val KEY_GLASS_STRENGTH = "glass_strength"
        private const val KEY_ICON_STYLE = "icon_style"
    }
}
