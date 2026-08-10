package com.xiaoiubao.suixinji.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class EventDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
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
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_events_event_time ON events(event_time)")
        db.execSQL("CREATE INDEX idx_events_created_at ON events(created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
                    createdAt = cursor.getLong(createdAtIndex)
                )
            }
        }
        return items
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

    private fun EventNote.toContentValues(includeCreatedAt: Boolean) = ContentValues().apply {
        put("title", title.trim())
        put("details", details.trim())
        put("location", location.trim())
        if (eventTime == null) putNull("event_time") else put("event_time", eventTime)
        put("reminder_enabled", if (reminderEnabled) 1 else 0)
        put("completed", if (completed) 1 else 0)
        if (includeCreatedAt) put("created_at", createdAt)
    }

    companion object {
        private const val DATABASE_NAME = "suixinji.db"
        private const val DATABASE_VERSION = 1
    }
}
