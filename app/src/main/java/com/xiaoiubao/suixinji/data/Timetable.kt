package com.xiaoiubao.suixinji.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class Semester(
    val id: Long = 0,
    val name: String = "新学期",
    // Blank is the explicit legacy weekly mode; migration must not guess a school's term dates.
    val startDate: String = "",
    val totalWeeks: Int = 20
) {
    fun validate() {
        require(name.isNotBlank() && name.length <= 100) { "学期名称须为 1–100 字" }
        require(totalWeeks in 1..60) { "学期周数须为 1–60" }
        if (startDate.isNotBlank()) {
            val date = LocalDate.parse(startDate)
            require(date.year in 1970..2200 && date.dayOfWeek == DayOfWeek.MONDAY) { "第一周起始日必须是周一（1970–2200 年）" }
        }
    }
    fun weekOn(date: LocalDate): Int? = if (startDate.isBlank()) null else
        Math.floorDiv(ChronoUnit.DAYS.between(LocalDate.parse(startDate), date), 7L).toInt() + 1

    companion object {
        fun newTerm() = Semester(startDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString())
        fun legacy() = Semester(id = 1, name = "原有课表 · 每周重复")
    }
}

data class CoursePeriod(val number: Int, val startMinute: Int, val endMinute: Int, val semesterId: Long = 1)

object Timetable {
    fun defaultPeriods(semesterId: Long = 1) = listOf(
        510 to 555, 565 to 610, 630 to 675, 685 to 730,
        870 to 915, 925 to 970, 1050 to 1095, 1105 to 1150
    ).mapIndexed { index, times -> CoursePeriod(index + 1, times.first, times.second, semesterId) }

    fun validatePeriods(periods: List<CoursePeriod>) {
        require(periods.size in 1..24) { "请设置 1–24 个节次" }
        periods.forEachIndexed { index, period ->
            require(period.number == index + 1 && period.startMinute in 0..1438 && period.endMinute in 1..1439 && period.startMinute < period.endMinute) { "请检查第 ${index + 1} 节起止时间" }
            if (index > 0) require(period.startMinute >= periods[index - 1].endMinute) { "节次时间不能重叠或倒序" }
        }
    }

    fun parseTime(value: String): Int {
        val match = Regex("^(\\d{1,2})[:：](\\d{2})$").matchEntire(value.trim()) ?: error("时间请使用 HH:mm 格式")
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        require(hour in 0..23 && minute in 0..59) { "时间超出范围" }
        return hour * 60 + minute
    }
    fun time(minute: Int) = "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

    fun parseWeeks(value: String, total: Int): List<Int> {
        require(total in 1..60 && value.length <= 500) { "周次超出范围" }
        var text = value.trim().replace('，', ',').replace('、', ',').replace('；', ',')
            .replace('～', '-').replace('~', '-').replace('—', '-').replace('－', '-').replace("至", "-")
        val odd = text.contains("单")
        val even = text.contains("双")
        require(!(odd && even)) { "请将不同单双周的课程分开导入" }
        text = text.replace(Regex("[第周单双()（）\\[\\]\\s]"), "")
        if (text.isEmpty() && (odd || even)) text = "1-$total"
        require(text.isNotEmpty()) { "请选择上课周次" }
        val weeks = sortedSetOf<Int>()
        text.split(',').forEach { part ->
            val range = Regex("^(\\d{1,2})(?:-(\\d{1,2}))?$").matchEntire(part) ?: error("周次示例：1-16、1-16(单)、1,3,8-12")
            val start = range.groupValues[1].toInt()
            val end = range.groupValues[2].ifBlank { range.groupValues[1] }.toInt()
            require(start in 1..total && end in start..total) { "上课周次须在 1–$total 周内" }
            (start..end).filterTo(weeks) { (!odd || it % 2 == 1) && (!even || it % 2 == 0) }
        }
        require(weeks.isNotEmpty()) { "上课周次不能为空" }
        return weeks.toList()
    }

    fun weeksText(weeks: List<Int>): String {
        val sorted = weeks.distinct().sorted()
        if (sorted.isEmpty()) return ""
        val ranges = mutableListOf<String>()
        var start = sorted.first(); var end = start
        for (week in sorted.drop(1)) {
            if (week == end + 1) end = week else {
                ranges += if (start == end) "$start" else "$start-$end"
                start = week; end = week
            }
        }
        ranges += if (start == end) "$start" else "$start-$end"
        return ranges.joinToString(",")
    }

    fun occursOn(course: Course, semester: Semester, date: LocalDate): Boolean {
        if (course.semesterId != semester.id || course.dayOfWeek != date.dayOfWeek.value) return false
        val week = semester.weekOn(date) ?: return true
        return week in 1..semester.totalWeeks && week in course.weeks
    }

    fun conflicts(first: Course, second: Course, weeklyMode: Boolean = false): Boolean =
        first.semesterId == second.semesterId && first.dayOfWeek == second.dayOfWeek &&
            first.startMinute < second.endMinute && second.startMinute < first.endMinute &&
            (weeklyMode || first.weeks.any { it in second.weeks })

    fun nextReminder(course: Course, semester: Semester, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (course.semesterId != semester.id) return null
        val dates = if (semester.startDate.isBlank()) {
            val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            (0L..8L).map(today::plusDays).filter { it.dayOfWeek.value == course.dayOfWeek }
        } else {
            val monday = LocalDate.parse(semester.startDate)
            course.weeks.distinct().sorted().filter { it in 1..semester.totalWeeks }
                .map { monday.plusWeeks((it - 1).toLong()).plusDays((course.dayOfWeek - 1).toLong()) }
        }
        return dates.map { date ->
            date.atTime(course.startMinute / 60, course.startMinute % 60).atZone(zone)
                .toInstant().toEpochMilli() - course.reminderMinutesBefore * 60000L
        }.firstOrNull { it > now }
    }
}
