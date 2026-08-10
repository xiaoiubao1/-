package com.xiaoiubao.suixinji.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object LauncherIconManager {
    fun apply(context: Context, selected: IconStyle) {
        val packageManager = context.packageManager
        IconStyle.entries.forEach { style ->
            val component = ComponentName(
                context.packageName,
                "${context.packageName}.${style.componentName}"
            )
            val state = if (style == selected) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching {
                packageManager.setComponentEnabledSetting(
                    component,
                    state,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
