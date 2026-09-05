package com.xiaoiubao.suixinji.reminder

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.xiaoiubao.suixinji.data.DataAccess
import kotlin.concurrent.withLock

/** Kept so persisted jobs from older installations can still load this class. */
class CourseReminderWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result = try {
        DataAccess.lock.withLock { ReminderDelivery.course(applicationContext, inputData.getLong(KEY_COURSE_ID, 0L)) }
        Result.success()
    } catch (e: Exception) { Result.retry() }
    companion object { const val KEY_COURSE_ID = "course_id" }
}
