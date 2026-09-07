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
import java.util.UUID
import kotlin.concurrent.withLock
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupService(private val context: Context) {
    companion object {
        private const val MAX_MANIFEST_BYTES = 8L * 1024 * 1024
        private const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
        private const val MAX_ENTRIES = 10000
    }

    fun exportEventsCsv(db: EventDatabase, uri: Uri): String {
        val events = DataAccess.lock.withLock { db.getAll() }
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
                            event.eventTime?.toString().orEmpty(),
                            event.reminderEnabled.toString(),
                            event.completed.toString(),
                            event.imageUri,
                            event.createdAt.toString()
                        ).joinToString(",") { CsvCodec.escape(it) }
                    )
                    writer.newLine()
                }
            }
        } ?: error("无法创建导出文件")
        return "已导出 ${events.size} 条记录到 CSV"
    }

    fun createBackup(db: EventDatabase, settings: AppSettings, uri: Uri): String {
        val snapshot = DataAccess.lock.withLock { BackupSnapshot(db.getAll(), db.getCourses(), db.getSemesters(), db.getPeriods(), settings.activeSemesterId) }
        val events = snapshot.events
        val courses = snapshot.courses
        val wallpaper = settings.wallpaper
        val widgetBackground = settings.widgetBackgroundUri
        val staging = File(context.cacheDir, "backup-${UUID.randomUUID()}").apply { check(mkdirs()) }
        var missingImages = 0
        fun stage(sourceUri: String, entry: String): String {
            if (sourceUri.isBlank()) return ""
            val target = safeTarget(staging, entry)
            return try {
                target.parentFile?.mkdirs()
                context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                } ?: error("图片无法读取")
                entry
            } catch (e: Exception) {
                target.delete()
                missingImages++
                ""
            }
        }
        try {
        val wallpaperEntry = stage(wallpaper, "media/wallpaper.bin")
        val widgetEntry = stage(widgetBackground, "media/widget-background.bin")

        val root = JSONObject().apply {
            put("format", "suixinji-backup")
            put("version", 4)
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
            val imageEntry = stage(event.imageUri, "media/event_$index.bin")
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
                put("semesterId", course.semesterId)
                put("weeks", JSONArray(course.weeks))
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
        root.put("activeSemesterId", snapshot.activeSemesterId)
        root.put("semesters", JSONArray().apply { snapshot.semesters.forEach { term -> put(JSONObject().apply {
            put("id", term.id); put("name", term.name); put("startDate", term.startDate); put("totalWeeks", term.totalWeeks)
        }) } })
        root.put("periods", JSONArray().apply { snapshot.periods.forEach { period -> put(JSONObject().apply {
            put("semesterId", period.semesterId); put("number", period.number)
            put("startMinute", period.startMinute); put("endMinute", period.endMinute)
        }) } })

        // Never report success for an archive our own restore would reject.
        val manifest = root.toString(2).toByteArray(Charsets.UTF_8)
        val mediaFiles = staging.walkTopDown().filter { it.isFile }.toList()
        require(manifest.size <= MAX_MANIFEST_BYTES) { "备份清单超过 8 MiB，无法生成可恢复的备份" }
        require(mediaFiles.size + 1 <= MAX_ENTRIES) { "备份文件数量过多" }
        require(mediaFiles.sumOf { it.length() } + manifest.size <= MAX_ARCHIVE_BYTES) { "备份内容超过 512 MiB" }
        context.contentResolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(raw).use { zip ->
                zip.putNextEntry(ZipEntry("backup.json"))
                zip.write(manifest)
                zip.closeEntry()
                mediaFiles.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(staging).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: error("无法创建备份文件")

        return "备份完成：${events.size} 条记录、${courses.size} 门课程" +
            if (missingImages > 0) "；有 $missingImages 张图片无法读取，未包含在备份中" else ""
        } finally { staging.deleteRecursively() }
    }

    fun restoreBackup(db: EventDatabase, settings: AppSettings, uri: Uri): String {
        val tempDir = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { check(mkdirs()) }
        // A fresh generation keeps all live images intact until the restore has committed.
        val mediaRoot = File(context.filesDir, "restored_media")
        val newMedia = File(mediaRoot, "generation-${UUID.randomUUID()}")
        var committed = false
        var settingsTouched = false
        val oldSettings = settings.snapshot()
        var missingImages = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw).use { zip ->
                    var total = 0L
                    var count = 0
                    val names = mutableSetOf<String>()
                    val buffer = ByteArray(8192)
                    var entry = zip.nextEntry
                    while (entry != null) {
                        require(++count <= MAX_ENTRIES) { "备份文件数量过多" }
                        val target = safeTarget(tempDir, entry.name)
                        require(names.add(target.canonicalPath)) { "备份含有重复文件" }
                        if (!entry.isDirectory) {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { output ->
                                var n = zip.read(buffer)
                                while (n != -1) {
                                    total += n
                                    require(total <= MAX_ARCHIVE_BYTES) { "备份解压后超过 512 MiB" }
                                    output.write(buffer, 0, n)
                                    n = zip.read(buffer)
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("无法读取备份文件")

            val rootFile = File(tempDir, "backup.json")
            require(rootFile.isFile && rootFile.length() <= MAX_MANIFEST_BYTES) { "备份清单不存在或过大" }
            val root = JSONObject(rootFile.readText(Charsets.UTF_8))
            require(root.text("format") == "suixinji-backup") { "备份格式不受支持" }
            val version = root.number("version", -1)
            require(version in 1L..4L) { "备份版本不受支持" }
            // Missing / mistyped arrays must not turn into an apparently successful empty restore.
            val eventsJson = root.getJSONArray("events")
            val coursesJson = root.getJSONArray("courses")
            val semesters = if (version < 4) listOf(Semester.legacy()) else root.getJSONArray("semesters").let { array ->
                require(array.length() in 1..100) { "学期数量无效" }
                (0 until array.length()).map { index -> array.getJSONObject(index).let { item ->
                    Semester(item.number("id", -1), item.text("name"), item.text("startDate"), item.integer("totalWeeks", -1))
                        .also { it.validate(); require(it.id in 1..1_000_000_000L) { "学期 ID 无效" } }
                } }
            }
            require(semesters.map { it.id }.distinct().size == semesters.size) { "重复学期" }
            val periods = if (version < 4) Timetable.defaultPeriods() else root.getJSONArray("periods").let { array ->
                require(array.length() <= 2400) { "节次数量过多" }
                (0 until array.length()).map { index -> array.getJSONObject(index).let { item ->
                    CoursePeriod(item.integer("number", -1), item.integer("startMinute", -1), item.integer("endMinute", -1), item.number("semesterId", -1))
                } }
            }
            require(periods.all { p -> semesters.any { it.id == p.semesterId } }) { "节次所属学期无效" }
            semesters.forEach { term -> Timetable.validatePeriods(periods.filter { it.semesterId == term.id }.sortedBy { it.number }) }
            val activeSemesterId = if (version < 4) 1L else root.number("activeSemesterId", -1)
            require(semesters.any { it.id == activeSemesterId }) { "当前学期无效" }
            val json = if (root.has("settings")) root.getJSONObject("settings") else null
            check(newMedia.mkdirs()) { "无法创建恢复目录" }
            fun media(entry: String, prefix: String): String {
                if (entry.isBlank()) return ""
                val source = safeTarget(tempDir, entry)
                require(entry.startsWith("media/")) { "非法的图片引用" }
                if (!source.isFile) { missingImages++; return "" }
                val target = File(newMedia, "$prefix-${UUID.randomUUID()}.bin")
                source.copyTo(target)
                return Uri.fromFile(target).toString()
            }
            val events = (0 until eventsJson.length()).map { index ->
                val item = eventsJson.getJSONObject(index)
                val title = item.text("title")
                require(title.isNotBlank()) { "记录标题为空" }
                EventNote(
                    title = title, details = item.text("details"), location = item.text("location"),
                    eventTime = if (!item.has("eventTime") || item.isNull("eventTime")) null else item.number("eventTime", 0),
                    reminderEnabled = item.flag("reminderEnabled", false),
                    completed = item.flag("completed", false),
                    imageUri = media(item.text("imageEntry"), "event-$index"),
                    createdAt = item.number("createdAt", System.currentTimeMillis())
                )
            }
            val courses = (0 until coursesJson.length()).map { index ->
                val item = coursesJson.getJSONObject(index)
                val day = item.integer("dayOfWeek", 1)
                val start = item.integer("startMinute", 480)
                val end = item.integer("endMinute", 540)
                val before = item.integer("reminderMinutesBefore", 10)
                val name = item.text("name")
                require(name.isNotBlank() && day in 1..7 && start in 0..1439 && end in 1..1439 && end > start && before in 0..180) { "课程数据无效" }
                Course(name = name, teacher = item.text("teacher"), location = item.text("location"),
                    dayOfWeek = day, startMinute = start, endMinute = end, note = item.text("note"),
                    reminderEnabled = item.flag("reminderEnabled", false), reminderMinutesBefore = before,
                    semesterId = if (version < 4) 1L else item.number("semesterId", -1),
                    weeks = if (version < 4) (1..20).toList() else item.getJSONArray("weeks").let { array ->
                        require(array.length() in 1..60) { "课程周次无效" }
                        (0 until array.length()).map { i ->
                            val value = array.get(i)
                            require(value is Number && value.toDouble() in 1.0..60.0 && value.toDouble() == value.toInt().toDouble()) { "课程周次无效" }
                            value.toInt()
                        }
                    }).also { course -> course.validate(semesters.firstOrNull { it.id == course.semesterId } ?: error("课程所属学期无效")) }
            }
            val nextSettings = oldSettings.toMutableMap()
            nextSettings["active_semester_id"] = activeSemesterId
            json?.let {
                val wallpaper = if (it.text("wallpaper") == "custom") media(it.text("wallpaperEntry"), "wallpaper") else ""
                val widget = if (it.text("widgetBackground") == "custom") media(it.text("widgetBackgroundEntry"), "widget") else ""
                nextSettings.putAll(mapOf(
                    "theme" to ThemePreset.valueOf(it.text("theme", ThemePreset.CREAM.name)).name,
                    "background_style" to BackgroundStyle.valueOf(it.text("backgroundStyle", BackgroundStyle.LIGHT.name)).name,
                    "glass_strength" to it.fraction("glassStrength", 0.60f, 0f),
                    "wallpaper" to wallpaper,
                    "custom_background_enabled" to (it.flag("customBackgroundEnabled", wallpaper.isNotBlank()) && wallpaper.isNotBlank()),
                    "widget_background_uri" to widget,
                    "widget_background_color" to it.integer("widgetBackgroundColor", 0xFFF4F1FA.toInt()),
                    "widget_text_mode" to WidgetTextMode.valueOf(it.text("widgetTextMode", WidgetTextMode.AUTO.name)).name,
                    "widget_accent_color" to it.integer("widgetAccentColor", 0xFF7B61D1.toInt()),
                    "widget_opacity" to it.fraction("widgetOpacity", 0.82f, 0.35f),
                    "widget_frosted" to it.flag("widgetFrosted", true)
                ))
            }
            // File copying/decompression must not hold the reminder delivery lock.
            DataAccess.lock.withLock {
                try {
                    db.replaceAll(events, courses, semesters, periods.sortedWith(compareBy({ it.semesterId }, { it.number }))) {
                        settingsTouched = true
                        settings.replace(nextSettings)
                    }
                    committed = true
                } catch (e: Exception) {
                    if (settingsTouched) {
                        try { settings.replace(oldSettings); settingsTouched = false }
                        catch (rollback: Exception) { e.addSuppressed(rollback) }
                    }
                    throw e
                }
            }
            // Old media is deliberately retained: settings from legacy backups without a settings
            // section, and Android process interruption, can still refer to an older generation.
            return "恢复完成：${events.size} 条记录、${courses.size} 门课程" +
                if (missingImages > 0) "；原备份缺少 $missingImages 张图片，已恢复可用内容" else ""
        } catch (e: Exception) {
            if (settingsTouched && !committed) {
                try { settings.replace(oldSettings) } catch (rollback: Exception) { e.addSuppressed(rollback) }
            }
            throw e
        } finally {
            if (!committed) newMedia.deleteRecursively()
            tempDir.deleteRecursively()
        }
    }

    private data class BackupSnapshot(val events: List<EventNote>, val courses: List<Course>, val semesters: List<Semester>, val periods: List<CoursePeriod>, val activeSemesterId: Long)

    private fun JSONObject.text(key: String, default: String = ""): String {
        if (!has(key)) return default
        val value = get(key)
        require(value is String) { "字段 $key 应为文字" }
        return value
    }

    private fun JSONObject.flag(key: String, default: Boolean): Boolean {
        if (!has(key)) return default
        val value = get(key)
        require(value is Boolean) { "字段 $key 应为开关" }
        return value
    }

    private fun JSONObject.number(key: String, default: Long): Long {
        if (!has(key)) return default
        val value = get(key)
        require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toLong().toDouble()) { "字段 $key 应为整数" }
        return value.toLong()
    }

    private fun JSONObject.integer(key: String, default: Int): Int {
        val value = number(key, default.toLong())
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "整数超出范围：$key" }
        return value.toInt()
    }

    private fun JSONObject.fraction(key: String, default: Float, min: Float): Float {
        if (!has(key)) return default
        val value = get(key)
        require(value is Number && value.toFloat().isFinite() && value.toFloat() in min..1f) { "字段 $key 超出范围" }
        return value.toFloat()
    }

    private fun safeTarget(base: File, name: String): File {
        val target = File(base, name)
        val basePath = base.canonicalFile.path + File.separator
        require(target.canonicalFile.path.startsWith(basePath)) { "备份包含非法路径" }
        return target
    }

}
