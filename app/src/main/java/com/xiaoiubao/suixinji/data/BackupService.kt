package com.xiaoiubao.suixinji.data

import android.content.Context
import android.net.Uri
import com.xiaoiubao.suixinji.settings.AppSettings
import com.xiaoiubao.suixinji.settings.BackgroundStyle
import com.xiaoiubao.suixinji.settings.ThemePreset
import com.xiaoiubao.suixinji.settings.WidgetTextMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupService(private val context: Context) {

    fun exportEventsCsv(db: EventDatabase, uri: Uri): String {
        val events = db.getAll()
        context.contentResolver.openOutputStream(uri)?.use { output ->
            BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                writer.write("\uFEFF")
                writer.write("title,details,location,eventTime,reminder,completed,imageUri,createdAt")
                writer.newLine()
                events.forEach { event ->
                    writer.write(
                        listOf(
                            event.title,
                            event.details,
                            event.location,
                            event.eventTime?.let(::formatDateTime).orEmpty(),
                            event.reminderEnabled.toString(),
                            event.completed.toString(),
                            event.imageUri,
                            event.createdAt.toString()
                        ).joinToString(",") { csvEscape(it) }
                    )
                    writer.newLine()
                }
            }
        } ?: error("无法创建导出文件")
        return "已导出 ${events.size} 条记录到 CSV"
    }

    fun createBackup(db: EventDatabase, settings: AppSettings, uri: Uri): String {
        val events = db.getAll()
        val courses = db.getCourses()
        val wallpaper = settings.wallpaper
        val widgetBackground = settings.widgetBackgroundUri
        val wallpaperEntry = if (wallpaper.isNotBlank()) "media/wallpaper.bin" else ""
        val widgetEntry = if (widgetBackground.isNotBlank()) "media/widget-background.bin" else ""

        val root = JSONObject().apply {
            put("format", "suixinji-backup")
            put("version", 3)
            put("createdAt", System.currentTimeMillis())
            put("settings", JSONObject().apply {
                put("theme", settings.theme.name)
                put("backgroundStyle", settings.backgroundStyle.name)
                put("customBackgroundEnabled", settings.customBackgroundEnabled)
                put("glassStrength", settings.glassStrength.toDouble())
                put("wallpaper", if (wallpaperEntry.isNotBlank()) "custom" else "none")
                put("wallpaperEntry", wallpaperEntry)
                put("widgetBackground", if (widgetEntry.isNotBlank()) "custom" else "color")
                put("widgetBackgroundEntry", widgetEntry)
                put("widgetBackgroundColor", settings.widgetBackgroundColor)
                put("widgetTextMode", settings.widgetTextMode.name)
                put("widgetAccentColor", settings.widgetAccentColor)
                put("widgetOpacity", settings.widgetOpacity.toDouble())
                put("widgetFrosted", settings.widgetFrosted)
            })
        }

        val eventArray = JSONArray()
        events.forEachIndexed { index, event ->
            val imageEntry = if (event.imageUri.isNotBlank()) "media/event_$index.bin" else ""
            eventArray.put(JSONObject().apply {
                put("title", event.title)
                put("details", event.details)
                put("location", event.location)
                put("eventTime", event.eventTime ?: JSONObject.NULL)
                put("reminderEnabled", event.reminderEnabled)
                put("completed", event.completed)
                put("createdAt", event.createdAt)
                put("imageEntry", imageEntry)
            })
        }
        root.put("events", eventArray)

        val courseArray = JSONArray()
        courses.forEach { course ->
            courseArray.put(JSONObject().apply {
                put("name", course.name)
                put("teacher", course.teacher)
                put("location", course.location)
                put("dayOfWeek", course.dayOfWeek)
                put("startMinute", course.startMinute)
                put("endMinute", course.endMinute)
                put("note", course.note)
                put("reminderEnabled", course.reminderEnabled)
                put("reminderMinutesBefore", course.reminderMinutesBefore)
            })
        }
        root.put("courses", courseArray)

        context.contentResolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(raw).use { zip ->
                zip.putNextEntry(ZipEntry("backup.json"))
                zip.write(root.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                if (wallpaperEntry.isNotBlank()) copyUriToZip(wallpaper, wallpaperEntry, zip)
                if (widgetEntry.isNotBlank()) copyUriToZip(widgetBackground, widgetEntry, zip)
                events.forEachIndexed { index, event ->
                    if (event.imageUri.isNotBlank()) copyUriToZip(event.imageUri, "media/event_$index.bin", zip)
                }
            }
        } ?: error("无法创建备份文件")

        return "备份完成：${events.size} 条记录、${courses.size} 门课程"
    }

    fun restoreBackup(db: EventDatabase, settings: AppSettings, uri: Uri): String {
        val tempDir = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val target = safeTarget(tempDir, entry.name)
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("无法读取备份文件")

            val rootFile = File(tempDir, "backup.json")
            require(rootFile.exists()) { "不是有效的随心记备份文件" }
            val root = JSONObject(rootFile.readText(Charsets.UTF_8))
            require(root.optString("format") == "suixinji-backup") { "备份格式不受支持" }

            val restoredMediaDir = File(context.filesDir, "restored_media").apply {
                deleteRecursively()
                mkdirs()
            }

            val eventsJson = root.optJSONArray("events") ?: JSONArray()
            val events = buildList {
                for (i in 0 until eventsJson.length()) {
                    val item = eventsJson.getJSONObject(i)
                    val image = restoreMedia(tempDir, item.optString("imageEntry"), restoredMediaDir, "event-$i")
                    add(EventNote(
                        title = item.optString("title"),
                        details = item.optString("details"),
                        location = item.optString("location"),
                        eventTime = if (item.isNull("eventTime") || !item.has("eventTime")) null else item.optLong("eventTime"),
                        reminderEnabled = item.optBoolean("reminderEnabled", false),
                        completed = item.optBoolean("completed", false),
                        imageUri = image,
                        createdAt = item.optLong("createdAt", System.currentTimeMillis())
                    ))
                }
            }

            val coursesJson = root.optJSONArray("courses") ?: JSONArray()
            val courses = buildList {
                for (i in 0 until coursesJson.length()) {
                    val item = coursesJson.getJSONObject(i)
                    add(Course(
                        name = item.optString("name"),
                        teacher = item.optString("teacher"),
                        location = item.optString("location"),
                        dayOfWeek = item.optInt("dayOfWeek", 1).coerceIn(1, 7),
                        startMinute = item.optInt("startMinute", 8 * 60).coerceIn(0, 1439),
                        endMinute = item.optInt("endMinute", 9 * 60).coerceIn(0, 1439),
                        note = item.optString("note"),
                        reminderEnabled = item.optBoolean("reminderEnabled", false),
                        reminderMinutesBefore = item.optInt("reminderMinutesBefore", 10).coerceIn(0, 180)
                    ))
                }
            }
            db.replaceAll(events, courses)

            root.optJSONObject("settings")?.let { json ->
                settings.theme = runCatching {
                    ThemePreset.valueOf(json.optString("theme", ThemePreset.CREAM.name))
                }.getOrDefault(ThemePreset.CREAM)
                settings.backgroundStyle = runCatching {
                    BackgroundStyle.valueOf(json.optString("backgroundStyle", BackgroundStyle.LIGHT.name))
                }.getOrDefault(BackgroundStyle.LIGHT)
                settings.glassStrength = json.optDouble("glassStrength", 0.60).toFloat().coerceIn(0f, 1f)

                val restoredWallpaper = if (json.optString("wallpaper") == "custom") {
                    restoreMedia(tempDir, json.optString("wallpaperEntry"), restoredMediaDir, "wallpaper")
                } else ""
                settings.wallpaper = restoredWallpaper
                settings.customBackgroundEnabled = json.optBoolean("customBackgroundEnabled", restoredWallpaper.isNotBlank()) && restoredWallpaper.isNotBlank()

                val restoredWidget = if (json.optString("widgetBackground") == "custom") {
                    restoreMedia(tempDir, json.optString("widgetBackgroundEntry"), restoredMediaDir, "widget-background")
                } else ""
                settings.widgetBackgroundUri = restoredWidget
                settings.widgetBackgroundColor = json.optInt("widgetBackgroundColor", 0xFFF4F1FA.toInt())
                settings.widgetTextMode = runCatching {
                    WidgetTextMode.valueOf(json.optString("widgetTextMode", WidgetTextMode.AUTO.name))
                }.getOrDefault(WidgetTextMode.AUTO)
                settings.widgetAccentColor = json.optInt("widgetAccentColor", 0xFF7B61D1.toInt())
                settings.widgetOpacity = json.optDouble("widgetOpacity", 0.82).toFloat().coerceIn(0.35f, 1f)
                settings.widgetFrosted = json.optBoolean("widgetFrosted", true)
            }

            return "恢复完成：${events.size} 条记录、${courses.size} 门课程"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun copyUriToZip(uriString: String, entryName: String, zip: ZipOutputStream) {
        val input = runCatching { context.contentResolver.openInputStream(Uri.parse(uriString)) }.getOrNull() ?: return
        input.use {
            zip.putNextEntry(ZipEntry(entryName))
            try { it.copyTo(zip) } finally { zip.closeEntry() }
        }
    }

    private fun restoreMedia(tempDir: File, entryName: String, restoredMediaDir: File, prefix: String): String {
        if (entryName.isBlank()) return ""
        val source = runCatching { safeTarget(tempDir, entryName) }.getOrNull() ?: return ""
        if (!source.exists()) return ""
        val target = File(restoredMediaDir, "$prefix-${UUID.randomUUID()}.bin")
        source.copyTo(target, overwrite = true)
        return Uri.fromFile(target).toString()
    }

    private fun safeTarget(base: File, name: String): File {
        val target = File(base, name)
        val basePath = base.canonicalFile.path + File.separator
        require(target.canonicalFile.path.startsWith(basePath)) { "备份包含非法路径" }
        return target
    }

    private fun formatDateTime(value: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
    private fun csvEscape(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value
}
