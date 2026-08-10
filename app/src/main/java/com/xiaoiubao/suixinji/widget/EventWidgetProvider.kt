package com.xiaoiubao.suixinji.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.widget.RemoteViews
import com.xiaoiubao.suixinji.MainActivity
import com.xiaoiubao.suixinji.R
import com.xiaoiubao.suixinji.data.EventDatabase
import com.xiaoiubao.suixinji.settings.AppSettings
import com.xiaoiubao.suixinji.settings.WidgetTextMode
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

            val settings = AppSettings(context)
            val views = RemoteViews(context.packageName, R.layout.widget_event)
            views.setImageViewBitmap(R.id.widget_background_image, buildBackground(context, settings))

            val textColor = resolveTextColor(settings)
            val accentColor = settings.widgetAccentColor
            views.setTextColor(R.id.widget_label, accentColor)
            views.setTextColor(R.id.widget_event_title, textColor)
            views.setTextColor(R.id.widget_event_meta, withAlpha(textColor, 0.78f))
            views.setTextColor(R.id.widget_course, accentColor)

            views.setTextViewText(R.id.widget_event_title, event?.title?.ifBlank { "暂无待办" } ?: "暂无待办")
            val eventMeta = when {
                event == null -> "打开随心记添加一件事"
                event.eventTime != null -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(event.eventTime))
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

        private fun buildBackground(context: Context, settings: AppSettings): Bitmap {
            val width = 900
            val height = 420
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val radius = 56f
            val path = Path().apply { addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(path)

            val backgroundColor = applyOpacity(settings.widgetBackgroundColor, settings.widgetOpacity)
            canvas.drawColor(backgroundColor)

            val custom = settings.widgetBackgroundUri.takeIf { it.isNotBlank() }?.let { uri ->
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream)
                }.getOrNull()
            }
            if (custom != null) {
                drawCenterCrop(canvas, custom, width, height, settings.widgetOpacity)
                custom.recycle()
            }

            if (settings.widgetFrosted) {
                val overlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (resolveTextColor(settings) == Color.WHITE) Color.argb(48, 255, 255, 255)
                    else Color.argb(42, 255, 255, 255)
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlay)
            }
            canvas.restore()
            return bitmap
        }

        private fun drawCenterCrop(canvas: Canvas, source: Bitmap, width: Int, height: Int, opacity: Float) {
            val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
            val dx = (width - source.width * scale) / 2f
            val dy = (height - source.height * scale) / 2f
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(dx, dy)
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (255 * opacity.coerceIn(0.35f, 1f)).toInt()
            }
            canvas.drawBitmap(source, matrix, paint)
        }

        private fun resolveTextColor(settings: AppSettings): Int = when (settings.widgetTextMode) {
            WidgetTextMode.LIGHT -> Color.WHITE
            WidgetTextMode.DARK -> Color.rgb(34, 34, 34)
            WidgetTextMode.AUTO -> {
                if (settings.widgetBackgroundUri.isNotBlank()) Color.WHITE
                else if (luminance(settings.widgetBackgroundColor) < 0.5) Color.WHITE else Color.rgb(34, 34, 34)
            }
        }

        private fun luminance(color: Int): Double {
            val r = Color.red(color) / 255.0
            val g = Color.green(color) / 255.0
            val b = Color.blue(color) / 255.0
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        private fun applyOpacity(color: Int, opacity: Float): Int = Color.argb(
            (255 * opacity.coerceIn(0.35f, 1f)).toInt(),
            Color.red(color), Color.green(color), Color.blue(color)
        )

        private fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
            (255 * alpha.coerceIn(0f, 1f)).toInt(),
            Color.red(color), Color.green(color), Color.blue(color)
        )

        private fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
    }
}
