package com.xiaoiubao.suixinji.data

data class Course(
    val id: Long = 0,
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int = 1,
    val startMinute: Int = 8 * 60,
    val endMinute: Int = 9 * 60,
    val note: String = ""
)
