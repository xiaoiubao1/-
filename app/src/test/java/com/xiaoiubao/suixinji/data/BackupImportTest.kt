package com.xiaoiubao.suixinji.data

import android.app.Application
import android.net.Uri
import com.xiaoiubao.suixinji.settings.AppSettings
import com.xiaoiubao.suixinji.settings.ThemePreset
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupImportTest {
    private lateinit var app: Application
    private lateinit var db: EventDatabase
    private lateinit var settings: AppSettings
    private lateinit var backup: BackupService
    private lateinit var oldImage: File
    private lateinit var old: EventNote

    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.deleteDatabase("suixinji.db")
        db = EventDatabase(app)
        settings = AppSettings(app)
        settings.replace(emptyMap<String, Any>())
        settings.theme = ThemePreset.SAKURA
        backup = BackupService(app)
        File(app.filesDir, "restored_media").deleteRecursively()
        oldImage = File(app.filesDir, "restored_media/existing/old.bin").apply {
            parentFile!!.mkdirs()
            writeText("old image bytes")
        }
        val note = EventNote(title = "原记录", imageUri = Uri.fromFile(oldImage).toString(), createdAt = 1234)
        old = note.copy(id = db.insert(note))
        db.insertCourse(Course(name = "原课程"))
    }

    @After fun cleanup() { db.close() }

    private fun archive(root: JSONObject, extra: Map<String, ByteArray> = emptyMap()): Uri {
        val file = File(app.cacheDir, UUID.randomUUID().toString() + ".suixinji")
        ZipOutputStream(file.outputStream()).use { zip ->
            (mapOf("backup.json" to root.toString().toByteArray()) + extra).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
        return Uri.fromFile(file)
    }

    private fun root(): JSONObject = JSONObject().put("format", "suixinji-backup").put("version", 3)
        .put("events", JSONArray()).put("courses", JSONArray())

    private fun assertOriginalData() {
        assertEquals(listOf(old), db.getAll())
        assertEquals("原课程", db.getCourses().single().name)
        assertEquals("old image bytes", oldImage.readText())
        assertEquals(ThemePreset.SAKURA, settings.theme)
    }

    @Test fun missingOrMistypedArraysNeverBecomeAnEmptyRestore() {
        for (key in listOf("events", "courses")) {
            val missing = root().apply { remove(key) }
            assertThrows(Exception::class.java) { backup.restoreBackup(db, settings, archive(missing)) }
            assertOriginalData()
            assertThrows(Exception::class.java) { backup.restoreBackup(db, settings, archive(root().put(key, JSONObject()))) }
            assertOriginalData()
        }
    }

    @Test fun malformedRecordAfterMediaCopyPreservesOriginalDataAndImages() {
        val events = JSONArray().put(JSONObject().put("title", "新记录").put("imageEntry", "media/new.bin"))
            .put(JSONObject().put("title", 123))
        assertThrows(Exception::class.java) {
            backup.restoreBackup(db, settings, archive(root().put("events", events), mapOf("media/new.bin" to byteArrayOf(1, 2))))
        }
        assertOriginalData()
        assertFalse(File(app.filesDir, "restored_media").listFiles()!!.any { it.name.startsWith("generation-") })
    }

    @Test fun zipTraversalAndInvalidMediaReferenceAreRejected() {
        assertThrows(Exception::class.java) {
            backup.restoreBackup(db, settings, archive(root(), mapOf("../outside.bin" to byteArrayOf(1))))
        }
        assertOriginalData()
        val entry = JSONObject().put("title", "新记录").put("imageEntry", "../outside.bin")
        assertThrows(Exception::class.java) { backup.restoreBackup(db, settings, archive(root().put("events", JSONArray().put(entry)))) }
        assertOriginalData()
    }

    @Test fun unsupportedVersionAndOverflowingCourseFieldsAreRejected() {
        assertThrows(Exception::class.java) { backup.restoreBackup(db, settings, archive(root().put("version", 4294967299L))) }
        assertOriginalData()
        val course = JSONObject().put("name", "无效课程").put("dayOfWeek", 4294967297L)
        assertThrows(Exception::class.java) { backup.restoreBackup(db, settings, archive(root().put("courses", JSONArray().put(course)))) }
        assertOriginalData()
    }

    @Test fun invalidSettingsRollBackBeforeReplacingRecords() {
        val invalid = root().put("settings", JSONObject().put("theme", "UNKNOWN"))
        assertThrows(Exception::class.java) { backup.restoreBackup(db, settings, archive(invalid)) }
        assertOriginalData()
    }

    @Test fun databaseFailureBeforeCommitRollsBackBothTables() {
        assertThrows(IllegalStateException::class.java) {
            db.replaceAll(listOf(EventNote(title = "覆盖")), emptyList()) { error("simulated settings failure") }
        }
        assertOriginalData()
    }

    @Test fun fullBackupRestoresRecordsCoursesMediaAndSettings() {
        val uri = Uri.fromFile(File(app.cacheDir, "roundtrip.suixinji"))
        backup.createBackup(db, settings, uri)
        db.replaceAll(emptyList(), emptyList())
        settings.theme = ThemePreset.DARK
        val result = backup.restoreBackup(db, settings, uri)
        assertTrue(result.startsWith("恢复完成"))
        val restored = db.getAll().single()
        assertEquals(old.copy(id = restored.id, imageUri = restored.imageUri), restored)
        assertNotEquals(old.imageUri, restored.imageUri)
        assertEquals("old image bytes", File(Uri.parse(restored.imageUri).path!!).readText())
        assertEquals("原课程", db.getCourses().single().name)
        assertEquals(ThemePreset.SAKURA, settings.theme)
    }

    @Test fun missingImageIsReportedOnBackupAndRestore() {
        db.update(old.copy(imageUri = Uri.fromFile(File(app.filesDir, "does-not-exist")).toString()))
        val uri = Uri.fromFile(File(app.cacheDir, "missing-image.suixinji"))
        assertTrue(backup.createBackup(db, settings, uri).contains("1 张图片无法读取"))
        ZipInputStream(File(uri.path!!).inputStream()).use { zip ->
            assertEquals("backup.json", zip.nextEntry.name)
            val manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
            assertEquals("", manifest.getJSONArray("events").getJSONObject(0).getString("imageEntry"))
        }
        val event = JSONObject().put("title", "带缺图记录").put("imageEntry", "media/missing.bin")
        val message = backup.restoreBackup(db, settings, archive(root().put("events", JSONArray().put(event))))
        assertTrue(message.contains("缺少 1 张图片"))
        assertEquals("", db.getAll().single().imageUri)
    }

    @Test fun csvExportAndImportPreserveMultilineTextTimestampsAndFlags() {
        val event = EventNote(title = "标题,含\"引号\"", details = "第一行\r\n第二行\n第三行", location = "教室,1",
            eventTime = 1893456123456L, reminderEnabled = true, completed = true,
            imageUri = old.imageUri, createdAt = 1700000000123L)
        db.replaceAll(listOf(event), emptyList())
        val uri = Uri.fromFile(File(app.cacheDir, "roundtrip.csv"))
        backup.exportEventsCsv(db, uri)
        db.replaceAll(emptyList(), emptyList())
        val result = ImportService(app).importInto(db, uri)
        assertEquals(1, result.imported)
        val restored = db.getAll().single()
        assertEquals(event.copy(id = restored.id), restored)
    }

    @Test fun oversizedManifestIsRejectedBeforeCreatingAnUnrestorableBackup() {
        db.update(old.copy(details = "x".repeat(8 * 1024 * 1024)))
        val file = File(app.cacheDir, "oversized.suixinji")
        assertThrows(IllegalArgumentException::class.java) { backup.createBackup(db, settings, Uri.fromFile(file)) }
        assertFalse(file.exists())
        assertEquals("old image bytes", oldImage.readText())
    }

    @Test fun malformedCsvDoesNotPartiallyInsertRecords() {
        val file = File(app.cacheDir, "broken.csv").apply { writeText("title,details\n有效,第一条\n\"未闭合") }
        val result = ImportService(app).importInto(db, Uri.fromFile(file))
        assertEquals(0, result.imported)
        assertTrue(result.message.startsWith("导入失败"))
        assertOriginalData()
    }
}
