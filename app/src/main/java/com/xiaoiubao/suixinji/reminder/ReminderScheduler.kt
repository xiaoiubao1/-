package com.xiaoiubao.suixinji.reminder

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.xiaoiubao.suixinji.data.EventNote
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private fun workName(id: Long) = "event-reminder-$id"

    fun schedule(context: Context, note: EventNote) {
        cancel(context, note.id)

        val whenMillis = note.eventTime ?: return
        if (!note.reminderEnabled || note.completed || whenMillis <= System.currentTimeMillis()) return

        val delay = whenMillis - System.currentTimeMillis()
        val data = Data.Builder()
            .putLong(EventReminderWorker.KEY_EVENT_ID, note.id)
            .putString(EventReminderWorker.KEY_TITLE, note.title)
            .putString(EventReminderWorker.KEY_DETAILS, note.details)
            .putString(EventReminderWorker.KEY_LOCATION, note.location)
            .build()

        val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(workName(note.id))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(note.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, id: Long) {
        if (id > 0) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        }
    }
}
