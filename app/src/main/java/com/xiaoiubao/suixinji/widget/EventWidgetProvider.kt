package com.xiaoiubao.suixinji.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.xiaoiubao.suixinji.MainActivity
import com.xiaoiubao.suixinji.R
import com.xiaoiubao.suixinji.data.EventDatabase
import java.text.DateFormat
import java.util.Date

class EventWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateOne(context, appWidgetManager, id) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, EventWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { id -> updateOne(context, manager, id) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val db = EventDatabase(context)
            val event = runCatching { db.getNextEvent() }.getOrNull()
            val course = runCatching { db.getNextCourseToday() }.getOrNull()
            db.close()

            val views = RemoteViews(context.packageName, R.layout.widget_event)
            views.setTextViewText(
                R.id.widget_event_title,
                event?.title?.ifBlank { "暂无待办" } ?: "暂无待办"
            )
            val eventMeta = when {
                event == null -> "打开随心记添加一件事"
                event.eventTime != null -> DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT
                ).format(Date(event.eventTime))
                event.location.isNotBlank() -> "地点：${event.location}"
                else -> "未设置时间"
            }
            views.setTextViewText(R.id.widget_event_meta, eventMeta)

            val courseText = if (course == null) {
                "今天暂无后续课程"
            } else {
                val location = if (course.location.isBlank()) "" else " · ${course.location}"
                "${formatMinute(course.startMinute)} ${course.name}$location"
            }
            views.setTextViewText(R.id.widget_course, courseText)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                event?.id?.takeIf { it > 0 }?.let { putExtra(MainActivity.EXTRA_EVENT_ID, it) }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            manager.updateAppWidget(widgetId, views)
        }

        private fun formatMinute(minute: Int): String =
            "%02d:%02d".format(minute / 60, minute % 60)
    }
}
