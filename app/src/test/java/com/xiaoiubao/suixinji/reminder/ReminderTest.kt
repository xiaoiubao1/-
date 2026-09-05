package com.xiaoiubao.suixinji.reminder

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.work.Configuration
import androidx.work.WorkManager
import com.xiaoiubao.suixinji.MainActivity
import com.xiaoiubao.suixinji.data.EventDatabase
import com.xiaoiubao.suixinji.data.EventNote
import com.xiaoiubao.suixinji.data.Course
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReminderTest {
    private lateinit var app: Application
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.deleteDatabase("suixinji.db")
        try { WorkManager.getInstance(app) }
        catch (_: IllegalStateException) { WorkManager.initialize(app, Configuration.Builder().build()) }
    }

    @Test fun longNoteSchedulesWithSmallIntentAndNoTextPayload() {
        val at = System.currentTimeMillis() + 3600000L
        val note = EventNote(id = 7, title = "长".repeat(20000), details = "文".repeat(50000),
            location = "地点".repeat(10000), eventTime = at, reminderEnabled = true)
        ReminderScheduler.schedule(app, note)
        val alarm = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.single()
        val intent = shadowOf(alarm.operation).savedIntent
        assertEquals(at, intent.getLongExtra(ReminderAlarms.EXPECTED, 0))
        assertEquals(setOf(ReminderAlarms.EXPECTED, ReminderAlarms.REVISION), intent.extras!!.keySet())
        assertEquals("suixinji://alarm/event/7", intent.data.toString())
    }

    @Test @Config(sdk = [31])
    fun deniedExactPermissionFallsBackToAnInexactAlarm() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(ReminderAlarms.canScheduleExact(app))
        ReminderAlarms.schedule(app, "event", 9, System.currentTimeMillis() + 3600000L)
        val alarm = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.single()
        assertNotEquals(0L, alarm.windowLengthMs)
    }

    @Test fun notificationCourseAndWidgetClicksHaveDistinctIdentities() {
        val event = ReminderDelivery.clickIntent(app, "event", 1)
        val course = ReminderDelivery.clickIntent(app, "course", 1)
        val widget = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java).apply {
            data = Uri.parse("suixinji://widget/1")
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        assertNotEquals(event, course)
        assertNotEquals(event, widget)
        assertNotEquals(course, widget)
        assertEquals(1L, shadowOf(event).savedIntent.getLongExtra(MainActivity.EXTRA_EVENT_ID, -1))
        assertEquals(1L, shadowOf(course).savedIntent.getLongExtra(MainActivity.EXTRA_COURSE_ID, -1))
    }

    @Test fun staleAlarmForCompletedOrDeletedNoteDoesNotNotify() {
        EventDatabase(app).use { db ->
            val id = db.insert(EventNote(title = "已完成", completed = true, reminderEnabled = true,
                eventTime = System.currentTimeMillis() - 1000))
            ReminderDelivery.event(app, id)
            db.delete(id)
            ReminderDelivery.event(app, id)
        }
        assertEquals(0, shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.size)
    }

    @Test fun weeklyReminderKeepsLocalTimeAcrossDaylightSavingTransition() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val zone = ZoneId.of("America/Los_Angeles")
            val now = ZonedDateTime.of(2026, 3, 2, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
            val expected = ZonedDateTime.of(2026, 3, 9, 8, 50, 0, 0, zone).toInstant().toEpochMilli()
            val course = Course(dayOfWeek = 1, startMinute = 540, endMinute = 600, reminderMinutesBefore = 10)
            assertEquals(expected, CourseReminderScheduler.nextTriggerMillis(course, now))
        } finally { TimeZone.setDefault(previous) }
    }
}
