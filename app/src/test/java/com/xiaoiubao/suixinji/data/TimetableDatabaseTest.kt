package com.xiaoiubao.suixinji.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.xiaoiubao.suixinji.SchoolWebPolicy
import com.xiaoiubao.suixinji.settings.AppSettings
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TimetableDatabaseTest {
    private lateinit var app: Application
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.deleteDatabase("suixinji.db")
        app.getSharedPreferences("suixinji_settings", 0).edit().clear().commit()
    }
    @Test fun databaseV3MigrationPreservesIdsNotesAndWeeklyReminders() {
        val file = app.getDatabasePath("suixinji.db"); file.parentFile!!.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            raw.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,details TEXT NOT NULL DEFAULT '',location TEXT NOT NULL DEFAULT '',event_time INTEGER,reminder_enabled INTEGER NOT NULL DEFAULT 0,completed INTEGER NOT NULL DEFAULT 0,image_uri TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL)")
            raw.execSQL("CREATE TABLE courses (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,teacher TEXT NOT NULL DEFAULT '',location TEXT NOT NULL DEFAULT '',day_of_week INTEGER NOT NULL,start_minute INTEGER NOT NULL,end_minute INTEGER NOT NULL,note TEXT NOT NULL DEFAULT '',reminder_enabled INTEGER NOT NULL DEFAULT 0,reminder_minutes_before INTEGER NOT NULL DEFAULT 10)")
            raw.execSQL("INSERT INTO events (id,title,created_at) VALUES (5,'旧记录',123)")
            raw.execSQL("INSERT INTO courses (id,name,day_of_week,start_minute,end_minute,reminder_enabled) VALUES (42,'旧数学',1,480,570,1)")
            raw.version = 3
        } finally { raw.close() }
        EventDatabase(app).use { db ->
            assertEquals("旧记录", db.getEvent(5)!!.title)
            assertEquals(42L, db.getCourses().single().id)
            assertTrue(db.getCourse(42)!!.reminderEnabled)
            assertEquals((1..20).toList(), db.getCourse(42)!!.weeks)
            assertEquals("", db.getSemesters().single().startDate)
            assertEquals(8, db.getPeriods().size)
        }
    }
    @Test fun invalidReplacementRollsBackAndAppendDeduplicates() {
        EventDatabase(app).use { db ->
            val old = db.insertCourse(Course(name = "旧课程"))
            val secondTerm = db.saveSemester(Semester.newTerm().copy(name = "下学期"), Timetable.defaultPeriods())
            db.insertCourse(Course(name = "其他学期", semesterId = secondTerm))
            val new = Course(name = "新课程", weeks = listOf(1, 3))
            assertTrue(runCatching { db.importCourses(1, listOf(new, new.copy(name = "错误", weeks = listOf(61))), true) }.isFailure)
            assertNotNull(db.getCourse(old))
            assertEquals(1, db.importCourses(1, listOf(new, new), false))
            assertEquals(0, db.importCourses(1, listOf(new), false))
            assertEquals(1, db.importCourses(1, listOf(new), true))
            assertEquals(setOf("新课程", "其他学期"), db.getCourses().map { it.name }.toSet())
            assertTrue(runCatching { db.importCourses(1, emptyList(), true) }.isFailure)
        }
    }
    @Test fun backupV4RoundTripsMultipleTermsPeriodsWeeksAndSelection() {
        EventDatabase(app).use { db ->
            val settings = AppSettings(app)
            val term = db.saveSemester(Semester(0, "秋季", "2026-09-07", 18), listOf(CoursePeriod(1, 480, 525), CoursePeriod(2, 540, 585)))
            settings.activeSemesterId = term
            db.insertCourse(Course(name = "单周课", semesterId = term, weeks = listOf(1, 3, 9)))
            val file = File(app.cacheDir, "terms.zip")
            val service = BackupService(app)
            service.createBackup(db, settings, Uri.fromFile(file))
            db.deleteSemester(term); settings.activeSemesterId = 1
            service.restoreBackup(db, settings, Uri.fromFile(file))
            assertEquals(term, settings.activeSemesterId)
            assertEquals(2, db.getSemesters().size)
            assertEquals("2026-09-07", db.getSemester(term)!!.startDate)
            assertEquals(listOf(1, 3, 9), db.getCourses().single().weeks)
            assertEquals(listOf(CoursePeriod(1, 480, 525, term), CoursePeriod(2, 540, 585, term)), db.getPeriods(term))
        }
    }
    @Test fun backupWithMissingTermArraysCannotOverwriteAnything() {
        EventDatabase(app).use { db ->
            val settings = AppSettings(app)
            db.insertCourse(Course(name = "必须保留"))
            val file = File(app.cacheDir, "valid.zip")
            val service = BackupService(app)
            service.createBackup(db, settings, Uri.fromFile(file))
            val root = ZipInputStream(file.inputStream()).use { zip -> zip.nextEntry; JSONObject(zip.readBytes().toString(Charsets.UTF_8)) }
            root.remove("semesters")
            val broken = File(app.cacheDir, "broken.zip")
            ZipOutputStream(broken.outputStream()).use { zip -> zip.putNextEntry(ZipEntry("backup.json")); zip.write(root.toString().toByteArray()); zip.closeEntry() }
            assertTrue(runCatching { service.restoreBackup(db, settings, Uri.fromFile(broken)) }.isFailure)
            assertEquals("必须保留", db.getCourses().single().name)
            assertEquals(1L, settings.activeSemesterId)
        }
    }
    @Test fun widgetSelectsOnlyActiveTermAndActualTeachingWeek() {
        val today = LocalDate.now()
        EventDatabase(app).use { db ->
            val term = db.saveSemester(Semester(0, "当前", today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(), 20), Timetable.defaultPeriods())
            AppSettings(app).activeSemesterId = term
            db.insertCourse(Course(name = "其他学期", dayOfWeek = today.dayOfWeek.value, startMinute = 0, endMinute = 1439))
            db.insertCourse(Course(name = "非本周", semesterId = term, dayOfWeek = today.dayOfWeek.value, startMinute = 1, endMinute = 1439, weeks = listOf(2)))
            db.insertCourse(Course(name = "本周课程", semesterId = term, dayOfWeek = today.dayOfWeek.value, startMinute = 2, endMinute = 1439, weeks = listOf(1)))
            assertEquals("本周课程", db.getNextCourseToday(today.atTime(12, 0))!!.name)
        }
    }
    @Test fun courseCsvRoundTripIsSeparateFromNoteCsv() {
        val semester = Semester.legacy()
        val course = Course(name = "数学,进阶", teacher = "张", note = "一\n二", weeks = listOf(1, 3, 9))
        val file = File(app.cacheDir, "courses.csv")
        val service = CourseImportService(app)
        service.export(Uri.fromFile(file), listOf(course))
        assertEquals(course, service.read(Uri.fromFile(file), semester, Timetable.defaultPeriods()).courses.single())
    }
    @Test fun schoolWebPolicyRejectsUnsafeAndCredentialBearingUrls() {
        assertTrue(SchoolWebPolicy.allows("https://jw.example.edu.cn/student"))
        listOf("http://jw.example.edu.cn", "javascript:alert(1)", "file:///sdcard/a", "content://notes/1", "intent://login", "https://student:password@jw.example.edu.cn", "https:///invalid").forEach {
            assertFalse(it, SchoolWebPolicy.allows(it))
        }
    }
}
