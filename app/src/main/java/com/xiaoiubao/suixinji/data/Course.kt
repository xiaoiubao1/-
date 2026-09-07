package com.xiaoiubao.suixinji.data

data class Course(
    val id: Long = 0,
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int = 1,
    val startMinute: Int = 8 * 60,
    val endMinute: Int = 9 * 60,
    val note: String = "",
    val reminderEnabled: Boolean = false,
    val reminderMinutesBefore: Int = 10,
    val semesterId: Long = 1,
    val weeks: List<Int> = (1..20).toList()
) {
    fun validate(semester: Semester) {
        require(semesterId == semester.id) { "课程所属学期不存在" }
        require(name.isNotBlank() && name.length <= 500) { "课程名称须为 1–500 字" }
        require(teacher.length <= 1000 && location.length <= 1000 && note.length <= 20000) { "课程文字过长" }
        require(dayOfWeek in 1..7 && startMinute in 0..1438 && endMinute in 1..1439 && endMinute > startMinute) { "请检查星期与起止时间" }
        require(reminderMinutesBefore in 0..180) { "提前提醒须为 0–180 分钟" }
        require(weeks.isNotEmpty() && weeks.size <= 60 && weeks.distinct().size == weeks.size && weeks.all { it in 1..semester.totalWeeks }) { "上课周次超出学期范围" }
    }
    fun importKey() = listOf(name.trim(), teacher.trim(), location.trim(), dayOfWeek.toString(), startMinute.toString(), endMinute.toString(), weeks.sorted().joinToString(","))
}
