package com.xiaoiubao.suixinji.reminder

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.xiaoiubao.suixinji.data.Course
import java.util.Calendar
import java.util.concurrent.TimeUnit

object CourseReminderScheduler {
    private fun workName(id: Long) = "course-reminder-$id"

    fun schedule(context: Context, course: Course) {
        cancel(context, course.id)
        if (!course.reminderEnabled || course.id <= 0L) return

        val triggerAt = nextTriggerMillis(course)
        val delay = (triggerAt - System.currentTimeMillis()).coerceAtLeast(1_000L)
        val data = Data.Builder()
            .putLong(CourseReminderWorker.KEY_COURSE_ID, course.id)
            .putString(CourseReminderWorker.KEY_NAME, course.name)
            .putString(CourseReminderWorker.KEY_LOCATION, course.location)
            .putInt(CourseReminderWorker.KEY_START_MINUTE, course.startMinute)
            .putInt(CourseReminderWorker.KEY_MINUTES_BEFORE, course.reminderMinutesBefore)
            .build()

        val request = OneTimeWorkRequestBuilder<CourseReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(workName(course.id))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(course.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, id: Long) {
        if (id > 0L) WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    private fun nextTriggerMillis(course: Course, nowMillis: Long = System.currentTimeMillis()): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, course.startMinute / 60)
            set(Calendar.MINUTE, course.startMinute % 60)
        }

        val androidDay = when (course.dayOfWeek.coerceIn(1, 7)) {
            1 -> Calendar.MONDAY
            2 -> Calendar.TUESDAY
            3 -> Calendar.WEDNESDAY
            4 -> Calendar.THURSDAY
            5 -> Calendar.FRIDAY
            6 -> Calendar.SATURDAY
            else -> Calendar.SUNDAY
        }
        val daysAhead = (androidDay - now.get(Calendar.DAY_OF_WEEK) + 7) % 7
        target.add(Calendar.DAY_OF_YEAR, daysAhead)
        target.add(Calendar.MINUTE, -course.reminderMinutesBefore.coerceIn(0, 180))
        if (target.timeInMillis <= nowMillis) target.add(Calendar.DAY_OF_YEAR, 7)
        return target.timeInMillis
    }
}
