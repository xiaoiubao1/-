package com.xiaoiubao.suixinji.reminder

import android.content.Context
import androidx.work.WorkManager
import com.xiaoiubao.suixinji.data.Course
import java.util.Calendar

object CourseReminderScheduler {
    private fun workName(id: Long) = "course-reminder-$id"

    fun schedule(context: Context, course: Course) {
        cancel(context, course.id)
        if (!course.reminderEnabled || course.id <= 0L) return
        ReminderAlarms.schedule(context, "course", course.id, nextTriggerMillis(course), revision(course))
    }

    fun cancel(context: Context, id: Long) {
        if (id <= 0) return
        ReminderAlarms.cancel(context, "course", id)
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    internal fun revision(course: Course): String =
        "${course.dayOfWeek}:${course.startMinute}:${course.endMinute}:${course.reminderMinutesBefore}"

    internal fun nextTriggerMillis(course: Course, nowMillis: Long = System.currentTimeMillis()): Long {
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
