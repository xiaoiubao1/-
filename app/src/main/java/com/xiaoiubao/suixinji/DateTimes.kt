package com.xiaoiubao.suixinji

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

object DateTimes {
    fun pickerDate(localMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(localMillis).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun localDate(utcMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    fun colorIndex(name: String, size: Int): Int = Math.floorMod(name.hashCode(), size)
}
