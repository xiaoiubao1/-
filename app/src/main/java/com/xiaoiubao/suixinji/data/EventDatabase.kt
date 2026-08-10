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
                note TEXT NOT NULL DEFAULT ''
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
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val titleIndex = cursor.getColumnIndexOrThrow("title")
            val detailsIndex = cursor.getColumnIndexOrThrow("details")
            val locationIndex = cursor.getColumnIndexOrThrow("location")
            val eventTimeIndex = cursor.getColumnIndexOrThrow("event_time")
            val reminderIndex = cursor.getColumnIndexOrThrow("reminder_enabled")
            val completedIndex = cursor.getColumnIndexOrThrow("completed")
            val imageUriIndex = cursor.getColumnIndexOrThrow("image_uri")
            val createdAtIndex = cursor.getColumnIndexOrThrow("created_at")

            while (cursor.moveToNext()) {
                items += EventNote(
                    id = cursor.getLong(idIndex),
                    title = cursor.getString(titleIndex),
                    details = cursor.getString(detailsIndex),
                    location = cursor.getString(locationIndex),
                    eventTime = if (cursor.isNull(eventTimeIndex)) null else cursor.getLong(eventTimeIndex),
                    reminderEnabled = cursor.getInt(reminderIndex) == 1,
                    completed = cursor.getInt(completedIndex) == 1,
                    imageUri = cursor.getString(imageUriIndex).orEmpty(),
                    createdAt = cursor.getLong(createdAtIndex)
                )
            }
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
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toEventNote() else null
        }
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
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toEventNote() else null
        }
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
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val teacherIndex = cursor.getColumnIndexOrThrow("teacher")
            val locationIndex = cursor.getColumnIndexOrThrow("location")
            val dayIndex = cursor.getColumnIndexOrThrow("day_of_week")
            val startIndex = cursor.getColumnIndexOrThrow("start_minute")
            val endIndex = cursor.getColumnIndexOrThrow("end_minute")
            val noteIndex = cursor.getColumnIndexOrThrow("note")
            while (cursor.moveToNext()) {
                items += Course(
                    id = cursor.getLong(idIndex),
                    name = cursor.getString(nameIndex),
                    teacher = cursor.getString(teacherIndex),
                    location = cursor.getString(locationIndex),
                    dayOfWeek = cursor.getInt(dayIndex),
                    startMinute = cursor.getInt(startIndex),
                    endMinute = cursor.getInt(endIndex),
                    note = cursor.getString(noteIndex)
                )
            }
        }
        return items
    }

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
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            Course(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                teacher = cursor.getString(cursor.getColumnIndexOrThrow("teacher")),
                location = cursor.getString(cursor.getColumnIndexOrThrow("location")),
                dayOfWeek = cursor.getInt(cursor.getColumnIndexOrThrow("day_of_week")),
                startMinute = cursor.getInt(cursor.getColumnIndexOrThrow("start_minute")),
                endMinute = cursor.getInt(cursor.getColumnIndexOrThrow("end_minute")),
                note = cursor.getString(cursor.getColumnIndexOrThrow("note"))
            )
        }
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
    }

    companion object {
        private const val DATABASE_NAME = "suixinji.db"
        private const val DATABASE_VERSION = 2
    }
}
