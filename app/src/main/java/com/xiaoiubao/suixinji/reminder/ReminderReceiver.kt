package com.xiaoiubao.suixinji.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaoiubao.suixinji.data.DataAccess
import com.xiaoiubao.suixinji.data.EventDatabase
import java.util.concurrent.Executors
import kotlin.concurrent.withLock

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.lastPathSegment?.toLongOrNull() ?: return
        if (id <= 0 || intent.action !in setOf(ReminderAlarms.EVENT, ReminderAlarms.COURSE)) return
        val pending = goAsync()
        executor.execute {
            try {
                DataAccess.lock.withLock {
                    if (intent.action == ReminderAlarms.EVENT)
                        ReminderDelivery.event(context, id, intent.getLongExtra(ReminderAlarms.EXPECTED, 0))
                    else ReminderDelivery.course(context, id, intent.getLongExtra(ReminderAlarms.EXPECTED, 0), intent.getStringExtra(ReminderAlarms.REVISION).orEmpty())
                }
            } catch (e: Exception) { android.util.Log.e("SuixinjiReminder", "Reminder delivery failed", e) }
            finally { pending.finish() }
        }
    }
    companion object { internal val executor = Executors.newSingleThreadExecutor() }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) return
        val pending = goAsync()
        ReminderReceiver.executor.execute {
            try {
                DataAccess.lock.withLock { rescheduleAll(context) }
            } catch (e: Exception) { android.util.Log.e("SuixinjiReminder", "Rescheduling failed", e) }
            finally { pending.finish() }
        }
    }
    companion object {
        fun rescheduleAll(context: Context) {
            EventDatabase(context).use { db ->
                db.getAll().forEach { ReminderScheduler.schedule(context, it) }
                db.getCourses().forEach { CourseReminderScheduler.schedule(context, it) }
            }
        }
    }
}
