package com.xiaoiubao.suixinji.reminder

import android.content.Context
import androidx.work.WorkManager
import com.xiaoiubao.suixinji.data.Course
import com.xiaoiubao.suixinji.data.EventDatabase
import com.xiaoiubao.suixinji.data.Semester
import com.xiaoiubao.suixinji.data.Timetable
import com.xiaoiubao.suixinji.settings.AppSettings

object CourseReminderScheduler {
    private fun workName(id: Long) = "course-reminder-$id"

    fun schedule(context: Context, course: Course) {
        cancel(context, course.id)
        if (!course.reminderEnabled || course.id <= 0L || course.semesterId != AppSettings(context).activeSemesterId) return
        val semester = EventDatabase(context).use { it.getSemester(course.semesterId) } ?: return
        val trigger = nextTriggerMillis(course, semester = semester) ?: return
        ReminderAlarms.schedule(context, "course", course.id, trigger, revision(course, semester))
    }

    fun cancel(context: Context, id: Long) {
        if (id <= 0) return
        ReminderAlarms.cancel(context, "course", id)
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    internal fun revision(course: Course, semester: Semester): String =
        "${course.semesterId}:${semester.startDate}:${semester.totalWeeks}:${course.weeks.sorted().joinToString(",")}:${course.dayOfWeek}:${course.startMinute}:${course.endMinute}:${course.reminderMinutesBefore}"

    internal fun nextTriggerMillis(course: Course, nowMillis: Long = System.currentTimeMillis(), semester: Semester = Semester.legacy()): Long? =
        Timetable.nextReminder(course, semester, nowMillis)
}
