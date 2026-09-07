package com.xiaoiubao.suixinji.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TimetableTest {
    @Test fun weeksSupportOddEvenRangesAndDiscontinuousWeeks() {
        assertEquals(listOf(1, 3, 5, 7), Timetable.parseWeeks("第1-8周(单)", 20))
        assertEquals(listOf(2, 4, 6, 8), Timetable.parseWeeks("1～8周（双周）", 20))
        assertEquals(listOf(1, 3, 8, 9, 10), Timetable.parseWeeks("1，3、8-10", 20))
        assertEquals("1,3,8-10", Timetable.weeksText(listOf(1, 3, 8, 9, 10)))
        listOf("", "0-3", "1-21", "3-1", "1-4单双", "1-3garbage").forEach { assertTrue(it, runCatching { Timetable.parseWeeks(it, 20) }.isFailure) }
    }
    @Test fun termWeeksCrossYearAndDoNotLeakBeforeOrAfterTerm() {
        val semester = Semester(1, "跨年学期", "2025-12-29", 3)
        val course = Course(name = "数学", dayOfWeek = 1, weeks = listOf(1, 3))
        assertEquals(0, semester.weekOn(LocalDate.parse("2025-12-28")))
        assertFalse(Timetable.occursOn(course, semester, LocalDate.parse("2025-12-22")))
        assertTrue(Timetable.occursOn(course, semester, LocalDate.parse("2025-12-29")))
        assertFalse(Timetable.occursOn(course, semester, LocalDate.parse("2026-01-05")))
        assertTrue(Timetable.occursOn(course, semester, LocalDate.parse("2026-01-12")))
        assertFalse(Timetable.occursOn(course, semester, LocalDate.parse("2026-01-19")))
    }
    @Test fun conflictsRequireSameTermDayTimeAndTeachingWeek() {
        val first = Course(name = "单周", startMinute = 480, endMinute = 570, weeks = listOf(1, 3))
        assertFalse(Timetable.conflicts(first, first.copy(name = "双周", weeks = listOf(2, 4))))
        assertFalse(Timetable.conflicts(first, first.copy(semesterId = 2)))
        assertFalse(Timetable.conflicts(first, first.copy(startMinute = 570, endMinute = 600)))
        assertTrue(Timetable.conflicts(first, first.copy(startMinute = 550)))
    }
    @Test fun remindersSkipOffWeeksAndRespectTermEndAndDst() {
        val zone = ZoneId.of("America/Los_Angeles")
        val semester = Semester(1, "春季", "2026-03-02", 4)
        val course = Course(name = "隔周", startMinute = 540, endMinute = 600, weeks = listOf(1, 3), reminderMinutesBefore = 10)
        val now = ZonedDateTime.of(2026, 3, 2, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 3, 16, 8, 50, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, Timetable.nextReminder(course, semester, now, zone))
        assertNull(Timetable.nextReminder(course, semester, expected, zone))
    }
    @Test fun midnightReminderMayFallOnPreviousSunday() {
        val zone = ZoneId.of("Asia/Shanghai")
        val semester = Semester(1, "春季", "2026-03-02", 1)
        val course = Course(name = "零点课程", startMinute = 5, endMinute = 50, weeks = listOf(1), reminderMinutesBefore = 10)
        val now = ZonedDateTime.of(2026, 3, 1, 23, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 3, 1, 23, 55, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, Timetable.nextReminder(course, semester, now, zone))
    }
    @Test fun legacyWeeklyModeDoesNotGuessSemesterDates() {
        val course = Course(name = "旧课程", weeks = listOf(1))
        assertTrue(Timetable.occursOn(course, Semester.legacy(), LocalDate.parse("2027-03-01")))
        assertNull(Semester.legacy().weekOn(LocalDate.now()))
    }
    @Test fun invalidTimesAndOverlappingPeriodsAreRejected() {
        listOf("25:00", "08:90", "8:3", "invalid").forEach { assertTrue(runCatching { Timetable.parseTime(it) }.isFailure) }
        assertTrue(runCatching { Timetable.validatePeriods(listOf(CoursePeriod(1, 500, 550), CoursePeriod(2, 540, 600))) }.isFailure)
        assertTrue(runCatching { Semester(1, "学期", "2026-03-03").validate() }.isFailure)
    }
}
