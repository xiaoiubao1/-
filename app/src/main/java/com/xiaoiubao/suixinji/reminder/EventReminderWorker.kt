package com.xiaoiubao.suixinji.reminder

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.xiaoiubao.suixinji.data.DataAccess
import kotlin.concurrent.withLock

/** Kept so persisted jobs from older installations can still load this class. */
class EventReminderWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result = try {
        DataAccess.lock.withLock { ReminderDelivery.event(applicationContext, inputData.getLong(KEY_EVENT_ID, 0L)) }
        Result.success()
    } catch (e: Exception) { Result.retry() }
    companion object { const val KEY_EVENT_ID = "event_id" }
}
