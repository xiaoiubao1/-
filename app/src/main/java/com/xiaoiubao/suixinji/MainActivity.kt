package com.xiaoiubao.suixinji

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.xiaoiubao.suixinji.settings.AppSettings

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        viewModel.refresh()
        setContent {
            SuixinjiRootV131(
                viewModel = viewModel,
                targetEventId = intent.getLongExtra(EXTRA_EVENT_ID, 0L),
                targetCourseId = intent.getLongExtra(EXTRA_COURSE_ID, 0L),
                settings = AppSettings(this)
            )
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_COURSE_ID = "extra_course_id"
    }
}
