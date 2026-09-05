package com.xiaoiubao.suixinji.reminder

import android.content.Context
import androidx.work.WorkManager
import com.xiaoiubao.suixinji.data.EventNote

object ReminderScheduler {
    fun schedule(context: Context, note: EventNote) {
        cancel(context, note.id)
        val at = note.eventTime ?: return
        if (note.id <= 0 || !note.reminderEnabled || note.completed || at <= System.currentTimeMillis()) return
        // Only identity/time enter the PendingIntent. Arbitrarily long note text stays in SQLite.
        ReminderAlarms.schedule(context, "event", note.id, at)
    }
    fun cancel(context: Context, id: Long) {
        if (id <= 0) return
        ReminderAlarms.cancel(context, "event", id)
        // Cancel jobs created by v1.3.2 when upgrading or editing existing records.
        WorkManager.getInstance(context).cancelUniqueWork("event-reminder-$id")
    }
}
