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

enum class WidgetTextMode(val title: String) {
    AUTO("自动"),
    DARK("深色"),
    LIGHT("浅色")
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
        get() = prefs.getString(KEY_WALLPAPER, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_WALLPAPER, value).apply() }

    var glassStrength: Float
        get() = prefs.getFloat(KEY_GLASS_STRENGTH, 0.60f).coerceIn(0f, 1f)
        set(value) { prefs.edit().putFloat(KEY_GLASS_STRENGTH, value.coerceIn(0f, 1f)).apply() }

    var widgetBackgroundUri: String
        get() = prefs.getString(KEY_WIDGET_BACKGROUND_URI, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_WIDGET_BACKGROUND_URI, value).apply() }

    var widgetBackgroundColor: Int
        get() = prefs.getInt(KEY_WIDGET_BACKGROUND_COLOR, 0xFFF4F1FA.toInt())
        set(value) { prefs.edit().putInt(KEY_WIDGET_BACKGROUND_COLOR, value).apply() }

    var widgetTextMode: WidgetTextMode
        get() = runCatching {
            WidgetTextMode.valueOf(
                prefs.getString(KEY_WIDGET_TEXT_MODE, null) ?: WidgetTextMode.AUTO.name
            )
        }.getOrDefault(WidgetTextMode.AUTO)
        set(value) { prefs.edit().putString(KEY_WIDGET_TEXT_MODE, value.name).apply() }

    var widgetAccentColor: Int
        get() = prefs.getInt(KEY_WIDGET_ACCENT_COLOR, 0xFF7B61D1.toInt())
        set(value) { prefs.edit().putInt(KEY_WIDGET_ACCENT_COLOR, value).apply() }

    var widgetOpacity: Float
        get() = prefs.getFloat(KEY_WIDGET_OPACITY, 0.82f).coerceIn(0.35f, 1f)
        set(value) { prefs.edit().putFloat(KEY_WIDGET_OPACITY, value.coerceIn(0.35f, 1f)).apply() }

    var widgetFrosted: Boolean
        get() = prefs.getBoolean(KEY_WIDGET_FROSTED, true)
        set(value) { prefs.edit().putBoolean(KEY_WIDGET_FROSTED, value).apply() }

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_BACKGROUND_STYLE = "background_style"
        private const val KEY_CUSTOM_BACKGROUND_ENABLED = "custom_background_enabled"
        private const val KEY_WALLPAPER = "wallpaper"
        private const val KEY_GLASS_STRENGTH = "glass_strength"
        private const val KEY_WIDGET_BACKGROUND_URI = "widget_background_uri"
        private const val KEY_WIDGET_BACKGROUND_COLOR = "widget_background_color"
        private const val KEY_WIDGET_TEXT_MODE = "widget_text_mode"
        private const val KEY_WIDGET_ACCENT_COLOR = "widget_accent_color"
        private const val KEY_WIDGET_OPACITY = "widget_opacity"
        private const val KEY_WIDGET_FROSTED = "widget_frosted"
    }
}
