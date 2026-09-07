package com.xiaoiubao.suixinji.data

data class PeriodDraft(val start: String, val end: String)
data class SemesterDraft(
    val original: Semester,
    val name: String = original.name,
    val startDate: String = original.startDate,
    val totalWeeks: String = original.totalWeeks.toString(),
    val periods: List<PeriodDraft> = Timetable.defaultPeriods().map { PeriodDraft(Timetable.time(it.startMinute), Timetable.time(it.endMinute)) }
) {
    fun parse(): Pair<Semester, List<CoursePeriod>> {
        val semester = original.copy(name = name.trim(), startDate = startDate.trim(), totalWeeks = totalWeeks.toIntOrNull() ?: error("请填写学期周数"))
        semester.validate()
        val parsed = periods.mapIndexed { index, period -> CoursePeriod(index + 1, Timetable.parseTime(period.start), Timetable.parseTime(period.end), original.id) }
        Timetable.validatePeriods(parsed)
        return semester to parsed
    }
    companion object {
        fun from(semester: Semester, periods: List<CoursePeriod>) = SemesterDraft(semester, periods = periods.map { PeriodDraft(Timetable.time(it.startMinute), Timetable.time(it.endMinute)) })
    }
}
