package com.xiaoiubao.suixinji.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xiaoiubao.suixinji.MainActivity
import com.xiaoiubao.suixinji.data.EventDatabase
import com.xiaoiubao.suixinji.data.Timetable
import com.xiaoiubao.suixinji.settings.AppSettings
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil

object ReminderDelivery {
    internal fun clickIntent(context: Context, kind: String, id: Long): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("suixinji://notification/$kind/$id")
            putExtra(if (kind == "event") MainActivity.EXTRA_EVENT_ID else MainActivity.EXTRA_COURSE_ID, id)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun event(context: Context, id: Long, expected: Long = 0L) {
        EventDatabase(context).use { db ->
            val note = db.getEvent(id) ?: return
            val at = note.eventTime ?: return
            if (!note.reminderEnabled || note.completed || (expected != 0L && at != expected)) return
            if (at > System.currentTimeMillis() + 1000) { ReminderScheduler.schedule(context, note); return }
            val text = listOf(note.details.take(3000), note.location.take(300)).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "你记录的事件时间到了" }
            notify(context, "event", id, note.title, text)
        }
    }

    fun course(context: Context, id: Long, trigger: Long = 0L, revision: String = "") {
        EventDatabase(context).use { db ->
            val course = db.getCourse(id) ?: return
            val semester = db.getSemester(course.semesterId) ?: return
            if (!course.reminderEnabled || course.semesterId != AppSettings(context).activeSemesterId ||
                (revision.isNotEmpty() && revision != CourseReminderScheduler.revision(course, semester))) return
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val date = Instant.ofEpochMilli(if (trigger > 0) trigger + course.reminderMinutesBefore * 60000L else now).atZone(zone).toLocalDate()
            if (!Timetable.occursOn(course, semester, date)) return
            val startAt = date.atTime(course.startMinute / 60, course.startMinute % 60).atZone(zone).toInstant().toEpochMilli()
            val endAt = date.atTime(course.endMinute / 60, course.endMinute % 60).atZone(zone).toInstant().toEpochMilli()
            if (trigger > 0 && startAt - course.reminderMinutesBefore * 60000L != trigger) return
            try {
                if (now >= startAt - course.reminderMinutesBefore * 60000L && now < endAt) {
                    val left = ceil((startAt - now) / 60000.0).toLong()
                    val time = "%02d:%02d".format(course.startMinute / 60, course.startMinute % 60)
                    val text = (if (left > 0) "$left 分钟后开始 · $time" else "课程已开始 · $time") +
                        if (course.location.isBlank()) "" else " · ${course.location.take(300)}"
                    notify(context, "course", id, course.name, text)
                }
            } finally { CourseReminderScheduler.schedule(context, course) }
        }
    }

    private fun notify(context: Context, kind: String, id: Long, title: String, text: String) {
        if (!NotificationTester.canNotify(context)) return
        val channelId = if (kind == "event") "event_reminders" else "course_reminders"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, if (kind == "event") "事件提醒" else "课程提醒", NotificationManager.IMPORTANCE_HIGH))
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title.take(300)).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
            .setContentIntent(clickIntent(context, kind, id)).build()
        try { NotificationManagerCompat.from(context).notify("$kind-$id", 1, notification) }
        catch (_: SecurityException) { /* Notification access was revoked while sending. */ }
    }
}
