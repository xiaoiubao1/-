package com.xiaoiubao.suixinji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.xiaoiubao.suixinji.settings.AppSettings

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.refresh()

        setContent {
            SuixinjiRoot(
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
