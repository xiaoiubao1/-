package com.xiaoiubao.suixinji.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

object ReminderAlarms {
    const val EVENT = "com.xiaoiubao.suixinji.EVENT_ALARM"
    const val COURSE = "com.xiaoiubao.suixinji.COURSE_ALARM"
    const val EXPECTED = "expected_time"
    const val REVISION = "revision"

    fun canScheduleExact(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    internal fun intent(context: Context, kind: String, id: Long): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            action = if (kind == "event") EVENT else COURSE
            data = Uri.parse("suixinji://alarm/$kind/$id")
        }

    fun schedule(context: Context, kind: String, id: Long, at: Long, revision: String = "") {
        val pending = PendingIntent.getBroadcast(context, 0, intent(context, kind, id).apply {
            putExtra(EXPECTED, at)
            putExtra(REVISION, revision)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (canScheduleExact(context)) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                return
            } catch (_: SecurityException) { /* Permission may be revoked between check and set. */ }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    fun cancel(context: Context, kind: String, id: Long) {
        val pending = PendingIntent.getBroadcast(context, 0, intent(context, kind, id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }
}
