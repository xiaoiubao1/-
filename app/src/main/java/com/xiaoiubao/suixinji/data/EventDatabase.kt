package com.xiaoiubao.suixinji.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar

class EventDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        createEventsTable(db)
        createCoursesTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE events ADD COLUMN image_uri TEXT NOT NULL DEFAULT ''")
            createCoursesTable(db)
        }
        if (oldVersion >= 2 && oldVersion < 3) {
            db.execSQL("ALTER TABLE courses ADD COLUMN reminder_enabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE courses ADD COLUMN reminder_minutes_before INTEGER NOT NULL DEFAULT 10")
        }
    }

    private fun createEventsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                details TEXT NOT NULL DEFAULT '',
                location TEXT NOT NULL DEFAULT '',
                event_time INTEGER,
                reminder_enabled INTEGER NOT NULL DEFAULT 0,
                completed INTEGER NOT NULL DEFAULT 0,
                image_uri TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_event_time ON events(event_time)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_created_at ON events(created_at)")
    }

    private fun createCoursesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS courses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                teacher TEXT NOT NULL DEFAULT '',
                location TEXT NOT NULL DEFAULT '',
                day_of_week INTEGER NOT NULL,
                start_minute INTEGER NOT NULL,
                end_minute INTEGER NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                reminder_enabled INTEGER NOT NULL DEFAULT 0,
                reminder_minutes_before INTEGER NOT NULL DEFAULT 10
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_courses_day_time ON courses(day_of_week, start_minute)"
        )
    }

    fun getAll(): List<EventNote> {
        val items = mutableListOf<EventNote>()
        readableDatabase.query(
            "events",
            null,
            null,
            null,
            null,
            null,
            "completed ASC, CASE WHEN event_time IS NULL THEN 1 ELSE 0 END, event_time ASC, created_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) items += cursor.toEventNote()
        }
        return items
    }

    fun getNextEvent(now: Long = System.currentTimeMillis()): EventNote? {
        val withTime = readableDatabase.query(
            "events",
            null,
            "completed = 0 AND event_time IS NOT NULL AND event_time >= ?",
            arrayOf(now.toString()),
            null,
            null,
            "event_time ASC",
            "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toEventNote() else null }
        if (withTime != null) return withTime

        return readableDatabase.query(
            "events",
            null,
            "completed = 0",
            null,
            null,
            null,
            "created_at DESC",
            "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toEventNote() else null }
    }

    fun insert(note: EventNote): Long =
        writableDatabase.insertOrThrow("events", null, note.toContentValues(includeCreatedAt = true))

    fun update(note: EventNote) {
        writableDatabase.update(
            "events",
            note.toContentValues(includeCreatedAt = false),
            "id = ?",
            arrayOf(note.id.toString())
        )
    }

    fun delete(id: Long) {
        writableDatabase.delete("events", "id = ?", arrayOf(id.toString()))
    }

    fun getCourses(): List<Course> {
        val items = mutableListOf<Course>()
        readableDatabase.query(
            "courses",
            null,
            null,
            null,
            null,
            null,
            "day_of_week ASC, start_minute ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) items += cursor.toCourse()
        }
        return items
    }

    fun getCourse(id: Long): Course? = readableDatabase.query(
        "courses",
        null,
        "id = ?",
        arrayOf(id.toString()),
        null,
        null,
        null,
        "1"
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toCourse() else null }

    fun getNextCourseToday(): Course? {
        val calendar = Calendar.getInstance()
        val day = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
        val minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return readableDatabase.query(
            "courses",
            null,
            "day_of_week = ? AND end_minute >= ?",
            arrayOf(day.toString(), minute.toString()),
            null,
            null,
            "start_minute ASC",
            "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toCourse() else null }
    }

    fun insertCourse(course: Course): Long =
        writableDatabase.insertOrThrow("courses", null, course.toContentValues())

    fun updateCourse(course: Course) {
        writableDatabase.update(
            "courses",
            course.toContentValues(),
            "id = ?",
            arrayOf(course.id.toString())
        )
    }

    fun deleteCourse(id: Long) {
        writableDatabase.delete("courses", "id = ?", arrayOf(id.toString()))
    }

    fun replaceAll(events: List<EventNote>, courses: List<Course>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("events", null, null)
            db.delete("courses", null, null)
            events.forEach { event ->
                db.insertOrThrow("events", null, event.copy(id = 0).toContentValues(includeCreatedAt = true))
            }
            courses.forEach { course ->
                db.insertOrThrow("courses", null, course.copy(id = 0).toContentValues())
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun android.database.Cursor.toEventNote() = EventNote(
        id = getLong(getColumnIndexOrThrow("id")),
        title = getString(getColumnIndexOrThrow("title")),
        details = getString(getColumnIndexOrThrow("details")),
        location = getString(getColumnIndexOrThrow("location")),
        eventTime = getColumnIndexOrThrow("event_time").let { if (isNull(it)) null else getLong(it) },
        reminderEnabled = getInt(getColumnIndexOrThrow("reminder_enabled")) == 1,
        completed = getInt(getColumnIndexOrThrow("completed")) == 1,
        imageUri = getString(getColumnIndexOrThrow("image_uri")).orEmpty(),
        createdAt = getLong(getColumnIndexOrThrow("created_at"))
    )

    private fun android.database.Cursor.toCourse() = Course(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        teacher = getString(getColumnIndexOrThrow("teacher")),
        location = getString(getColumnIndexOrThrow("location")),
        dayOfWeek = getInt(getColumnIndexOrThrow("day_of_week")),
        startMinute = getInt(getColumnIndexOrThrow("start_minute")),
        endMinute = getInt(getColumnIndexOrThrow("end_minute")),
        note = getString(getColumnIndexOrThrow("note")),
        reminderEnabled = getInt(getColumnIndexOrThrow("reminder_enabled")) == 1,
        reminderMinutesBefore = getInt(getColumnIndexOrThrow("reminder_minutes_before"))
    )

    private fun EventNote.toContentValues(includeCreatedAt: Boolean) = ContentValues().apply {
        put("title", title.trim())
        put("details", details.trim())
        put("location", location.trim())
        if (eventTime == null) putNull("event_time") else put("event_time", eventTime)
        put("reminder_enabled", if (reminderEnabled) 1 else 0)
        put("completed", if (completed) 1 else 0)
        put("image_uri", imageUri)
        if (includeCreatedAt) put("created_at", createdAt)
    }

    private fun Course.toContentValues() = ContentValues().apply {
        put("name", name.trim())
        put("teacher", teacher.trim())
        put("location", location.trim())
        put("day_of_week", dayOfWeek.coerceIn(1, 7))
        put("start_minute", startMinute.coerceIn(0, 1439))
        put("end_minute", endMinute.coerceIn(0, 1439))
        put("note", note.trim())
        put("reminder_enabled", if (reminderEnabled) 1 else 0)
        put("reminder_minutes_before", reminderMinutesBefore.coerceIn(0, 180))
    }

    companion object {
        private const val DATABASE_NAME = "suixinji.db"
        private const val DATABASE_VERSION = 3
    }
}
