package com.xiaoiubao.suixinji.data

import android.content.Context
import android.net.Uri
import com.xiaoiubao.suixinji.settings.AppSettings
import com.xiaoiubao.suixinji.settings.ThemePreset
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
        val root = JSONObject().apply {
            put("format", "suixinji-backup")
            put("version", 1)
            put("createdAt", System.currentTimeMillis())
        }

        val wallpaper = settings.wallpaper
        val wallpaperEntry = if (
            wallpaper.isNotBlank() &&
            wallpaper != AppSettings.WALLPAPER_NONE &&
            wallpaper != AppSettings.WALLPAPER_BUILTIN
        ) "media/wallpaper.bin" else ""

        root.put(
            "settings",
            JSONObject().apply {
                put("theme", settings.theme.name)
                put("wallpaper", when (wallpaper) {
                    AppSettings.WALLPAPER_NONE -> AppSettings.WALLPAPER_NONE
                    AppSettings.WALLPAPER_BUILTIN -> AppSettings.WALLPAPER_BUILTIN
                    else -> "custom"
                })
                put("wallpaperEntry", wallpaperEntry)
            }
        )

        val eventArray = JSONArray()
        events.forEachIndexed { index, event ->
            val imageEntry = if (event.imageUri.isNotBlank()) "media/event_$index.bin" else ""
            eventArray.put(
                JSONObject().apply {
                    put("title", event.title)
                    put("details", event.details)
                    put("location", event.location)
                    if (event.eventTime == null) put(JSONObject.NULL, JSONObject.NULL) else put("eventTime", event.eventTime)
                    put("reminderEnabled", event.reminderEnabled)
                    put("completed", event.completed)
                    put("createdAt", event.createdAt)
                    put("imageEntry", imageEntry)
                }
            )
        }
        root.put("events", eventArray)

        val courseArray = JSONArray()
        courses.forEach { course ->
            courseArray.put(
                JSONObject().apply {
                    put("name", course.name)
                    put("teacher", course.teacher)
                    put("location", course.location)
                    put("dayOfWeek", course.dayOfWeek)
                    put("startMinute", course.startMinute)
                    put("endMinute", course.endMinute)
                    put("note", course.note)
                    put("reminderEnabled", course.reminderEnabled)
                    put("reminderMinutesBefore", course.reminderMinutesBefore)
                }
            )
        }
        root.put("courses", courseArray)

        context.contentResolver.openOutputStream(uri)?.use { rawOutput ->
            ZipOutputStream(rawOutput).use { zip ->
                zip.putNextEntry(ZipEntry("backup.json"))
                zip.write(root.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                if (wallpaperEntry.isNotBlank()) {
                    copyUriToZip(wallpaper, wallpaperEntry, zip)
                }
                events.forEachIndexed { index, event ->
                    if (event.imageUri.isNotBlank()) {
                        copyUriToZip(event.imageUri, "media/event_$index.bin", zip)
                    }
                }
            }
        } ?: error("无法创建备份文件")

        return "备份完成：${events.size} 条记录、${courses.size} 门课程"
    }

    fun restoreBackup(db: EventDatabase, settings: AppSettings, uri: Uri): String {
        val tempDir = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            context.contentResolver.openInputStream(uri)?.use { rawInput ->
                ZipInputStream(rawInput).use { zip ->
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
                    val imageEntry = item.optString("imageEntry")
                    val restoredImage = restoreMedia(tempDir, imageEntry, restoredMediaDir, "event-$i")
                    add(
                        EventNote(
                            title = item.optString("title"),
                            details = item.optString("details"),
                            location = item.optString("location"),
                            eventTime = if (item.isNull("eventTime") || !item.has("eventTime")) null else item.optLong("eventTime"),
                            reminderEnabled = item.optBoolean("reminderEnabled", false),
                            completed = item.optBoolean("completed", false),
                            imageUri = restoredImage,
                            createdAt = item.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            val coursesJson = root.optJSONArray("courses") ?: JSONArray()
            val courses = buildList {
                for (i in 0 until coursesJson.length()) {
                    val item = coursesJson.getJSONObject(i)
                    add(
                        Course(
                            name = item.optString("name"),
                            teacher = item.optString("teacher"),
                            location = item.optString("location"),
                            dayOfWeek = item.optInt("dayOfWeek", 1).coerceIn(1, 7),
                            startMinute = item.optInt("startMinute", 8 * 60).coerceIn(0, 1439),
                            endMinute = item.optInt("endMinute", 9 * 60).coerceIn(0, 1439),
                            note = item.optString("note"),
                            reminderEnabled = item.optBoolean("reminderEnabled", false),
                            reminderMinutesBefore = item.optInt("reminderMinutesBefore", 10).coerceIn(0, 180)
                        )
                    )
                }
            }

            db.replaceAll(events, courses)

            val settingsJson = root.optJSONObject("settings")
            if (settingsJson != null) {
                settings.theme = runCatching {
                    ThemePreset.valueOf(settingsJson.optString("theme", ThemePreset.CREAM.name))
                }.getOrDefault(ThemePreset.CREAM)

                settings.wallpaper = when (settingsJson.optString("wallpaper")) {
                    AppSettings.WALLPAPER_NONE -> AppSettings.WALLPAPER_NONE
                    AppSettings.WALLPAPER_BUILTIN -> AppSettings.WALLPAPER_BUILTIN
                    "custom" -> {
                        restoreMedia(
                            tempDir,
                            settingsJson.optString("wallpaperEntry"),
                            restoredMediaDir,
                            "wallpaper"
                        ).ifBlank { AppSettings.WALLPAPER_BUILTIN }
                    }
                    else -> AppSettings.WALLPAPER_BUILTIN
                }
            }

            return "恢复完成：${events.size} 条记录、${courses.size} 门课程"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun copyUriToZip(uriString: String, entryName: String, zip: ZipOutputStream) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                zip.putNextEntry(ZipEntry(entryName))
                input.copyTo(zip)
                zip.closeEntry()
            }
        }
    }

    private fun restoreMedia(
        tempDir: File,
        entryName: String,
        restoredMediaDir: File,
        prefix: String
    ): String {
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

    private fun formatDateTime(value: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))

    private fun csvEscape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
}
