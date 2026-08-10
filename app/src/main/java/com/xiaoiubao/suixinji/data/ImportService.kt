package com.xiaoiubao.suixinji.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class ImportService(private val context: Context) {
    data class Result(val imported: Int, val skipped: Int, val message: String)

    fun importInto(database: EventDatabase, uri: Uri): Result {
        val name = displayName(uri).lowercase(Locale.getDefault())
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return Result(0, 0, "无法读取这个文件")

        return try {
            when {
                name.endsWith(".json") || text.trimStart().startsWith("[") || text.trimStart().startsWith("{") ->
                    importJson(database, text)
                name.endsWith(".csv") || text.lineSequence().firstOrNull().orEmpty().contains(",") ->
                    importCsv(database, text)
                else -> importText(database, text)
            }
        } catch (e: Exception) {
            Result(0, 0, "导入失败：${e.message ?: "文件格式无法识别"}")
        }
    }

    private fun importJson(database: EventDatabase, text: String): Result {
        val root = text.trim()
        val array = when {
            root.startsWith("[") -> JSONArray(root)
            else -> JSONObject(root).optJSONArray("events") ?: JSONArray().put(JSONObject(root))
        }
        var imported = 0
        var skipped = 0
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i)
            if (obj == null) {
                skipped++
                continue
            }
            val title = firstNonBlank(obj, "title", "标题", "name", "事件")
            if (title.isBlank()) {
                skipped++
                continue
            }
            database.insert(
                EventNote(
                    title = title,
                    details = firstNonBlank(obj, "details", "detail", "内容", "详细内容", "note"),
                    location = firstNonBlank(obj, "location", "地点", "位置"),
                    eventTime = parseTimeValue(obj.opt("eventTime") ?: obj.opt("event_time") ?: obj.opt("时间")),
                    reminderEnabled = parseBoolean(obj.opt("reminderEnabled") ?: obj.opt("reminder") ?: obj.opt("提醒")),
                    completed = parseBoolean(obj.opt("completed") ?: obj.opt("done") ?: obj.opt("已完成")),
                    imageUri = firstNonBlank(obj, "imageUri", "image_uri", "图片")
                )
            )
            imported++
        }
        return Result(imported, skipped, "JSON 导入完成：$imported 条，跳过 $skipped 条")
    }

    private fun importCsv(database: EventDatabase, text: String): Result {
        val rows = text.lineSequence()
            .filter { it.isNotBlank() }
            .map(::parseCsvLine)
            .toList()
        if (rows.isEmpty()) return Result(0, 0, "CSV 文件没有可导入内容")

        val knownHeaders = setOf(
            "title", "标题", "name", "事件", "details", "内容", "location", "地点",
            "eventtime", "event_time", "时间", "reminder", "提醒", "completed", "已完成"
        )
        val first = rows.first().map { it.trim().lowercase(Locale.getDefault()) }
        val hasHeader = first.any { it in knownHeaders }
        val header = if (hasHeader) first else emptyList()
        val dataRows = if (hasHeader) rows.drop(1) else rows

        fun indexOf(vararg aliases: String): Int = header.indexOfFirst { h -> aliases.any { h == it.lowercase(Locale.getDefault()) } }
        val titleIndex = if (hasHeader) indexOf("title", "标题", "name", "事件") else 0
        val detailsIndex = if (hasHeader) indexOf("details", "detail", "内容", "详细内容", "note") else 1
        val locationIndex = if (hasHeader) indexOf("location", "地点", "位置") else 2
        val timeIndex = if (hasHeader) indexOf("eventtime", "event_time", "time", "时间") else 3
        val reminderIndex = if (hasHeader) indexOf("reminderenabled", "reminder", "提醒") else 4
        val completedIndex = if (hasHeader) indexOf("completed", "done", "已完成") else 5

        fun List<String>.getSafe(index: Int): String = if (index >= 0 && index < size) this[index].trim() else ""

        var imported = 0
        var skipped = 0
        dataRows.forEach { row ->
            val title = row.getSafe(titleIndex)
            if (title.isBlank()) {
                skipped++
            } else {
                database.insert(
                    EventNote(
                        title = title,
                        details = row.getSafe(detailsIndex),
                        location = row.getSafe(locationIndex),
                        eventTime = parseTimeValue(row.getSafe(timeIndex)),
                        reminderEnabled = parseBoolean(row.getSafe(reminderIndex)),
                        completed = parseBoolean(row.getSafe(completedIndex))
                    )
                )
                imported++
            }
        }
        return Result(imported, skipped, "CSV 导入完成：$imported 条，跳过 $skipped 条")
    }

    private fun importText(database: EventDatabase, text: String): Result {
        var imported = 0
        text.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
            database.insert(EventNote(title = line))
            imported++
        }
        return Result(imported, 0, "文本导入完成：每行作为一条记录，共 $imported 条")
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result += current.toString()
        return result
    }

    private fun parseTimeValue(value: Any?): Long? {
        if (value == null || value == JSONObject.NULL) return null
        if (value is Number) return value.toLong().takeIf { it > 0 }
        val raw = value.toString().trim()
        raw.toLongOrNull()?.let { return it.takeIf { time -> time > 0 } }
        val formats = listOf("yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm", "yyyy-MM-dd", "yyyy/MM/dd")
        for (format in formats) {
            val parsed = runCatching {
                SimpleDateFormat(format, Locale.getDefault()).apply { isLenient = false }.parse(raw)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parseBoolean(value: Any?): Boolean {
        if (value is Boolean) return value
        if (value is Number) return value.toInt() != 0
        return when (value?.toString()?.trim()?.lowercase(Locale.getDefault())) {
            "1", "true", "yes", "y", "是", "开启", "已完成" -> true
            else -> false
        }
    }

    private fun firstNonBlank(obj: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            val value = obj.optString(key, "").trim()
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index).orEmpty()
        }
        return uri.lastPathSegment.orEmpty()
    }
}
