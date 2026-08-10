package com.xiaoiubao.suixinji.data

data class EventNote(
    val id: Long = 0,
    val title: String = "",
    val details: String = "",
    val location: String = "",
    val eventTime: Long? = null,
    val reminderEnabled: Boolean = false,
    val completed: Boolean = false,
    val imageUri: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
