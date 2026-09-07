package com.xiaoiubao.suixinji.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDateTime
import com.xiaoiubao.suixinji.settings.AppSettings

class EventDatabase(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), java.io.Closeable {

    override fun onCreate(db: SQLiteDatabase) {
        createEventsTable(db)
        createCoursesTable(db)
        createSemesterTables(db)
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
        if (oldVersion < 4) {
            if (oldVersion >= 2) {
                db.execSQL("ALTER TABLE courses ADD COLUMN semester_id INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE courses ADD COLUMN weeks TEXT NOT NULL DEFAULT '1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20'")
            }
            createSemesterTables(db)
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
                reminder_minutes_before INTEGER NOT NULL DEFAULT 10,
                semester_id INTEGER NOT NULL DEFAULT 1,
                weeks TEXT NOT NULL DEFAULT '1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20'
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

    fun getEvent(id: Long): EventNote? = readableDatabase.query(
        "events", null, "id = ?", arrayOf(id.toString()), null, null, null, "1"
    ).use { if (it.moveToFirst()) it.toEventNote() else null }

    fun <T> transaction(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = block()
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
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

    fun getNextCourseToday(now: LocalDateTime = LocalDateTime.now()): Course? {
        val semester = getSemester(AppSettings(context).activeSemesterId) ?: getSemesters().firstOrNull() ?: return null
        return getCourses().firstOrNull {
            Timetable.occursOn(it, semester, now.toLocalDate()) && it.endMinute > now.hour * 60 + now.minute
        }
    }

    fun insertCourse(course: Course): Long {
        course.validate(getSemester(course.semesterId) ?: error("课程所属学期不存在"))
        return writableDatabase.insertOrThrow("courses", null, course.toContentValues())
    }

    fun updateCourse(course: Course) {
        course.validate(getSemester(course.semesterId) ?: error("课程所属学期不存在"))
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

    fun replaceAll(events: List<EventNote>, courses: List<Course>, semesters: List<Semester>? = null, periods: List<CoursePeriod>? = null, beforeCommit: () -> Unit = {}) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("events", null, null)
            db.delete("courses", null, null)
            if (semesters != null && periods != null) {
                require(semesters.isNotEmpty()) { "备份缺少学期" }
                db.delete("periods", null, null)
                db.delete("semesters", null, null)
                semesters.forEach { term ->
                    term.validate()
                    db.insertOrThrow("semesters", null, term.toContentValues().apply { put("id", term.id) })
                    replacePeriods(term.id, periods.filter { it.semesterId == term.id })
                }
            }
            events.forEach { event ->
                db.insertOrThrow("events", null, event.copy(id = 0).toContentValues(includeCreatedAt = true))
            }
            courses.forEach { course ->
                insertCourse(course.copy(id = 0))
            }
            beforeCommit()
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
        reminderMinutesBefore = getInt(getColumnIndexOrThrow("reminder_minutes_before")),
        semesterId = getLong(getColumnIndexOrThrow("semester_id")),
        weeks = getString(getColumnIndexOrThrow("weeks")).split(',').map(String::toInt)
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
        put("semester_id", semesterId)
        put("weeks", weeks.sorted().joinToString(","))
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

    private fun createSemesterTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS semesters (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, start_date TEXT NOT NULL, total_weeks INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS periods (semester_id INTEGER NOT NULL, number INTEGER NOT NULL, start_minute INTEGER NOT NULL, end_minute INTEGER NOT NULL, PRIMARY KEY(semester_id, number))")
        db.insertOrThrow("semesters", null, Semester.legacy().toContentValues().apply { put("id", 1) })
        Timetable.defaultPeriods().forEach { db.insertOrThrow("periods", null, it.toContentValues()) }
    }

    fun getSemesters(): List<Semester> = readableDatabase.query("semesters", null, null, null, null, null, "id DESC").use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(Semester(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getInt(3)))
        }
    }
    fun getSemester(id: Long): Semester? = getSemesters().firstOrNull { it.id == id }
    fun getPeriods(semesterId: Long? = null): List<CoursePeriod> = readableDatabase.query(
        "periods", null, if (semesterId == null) null else "semester_id = ?", semesterId?.let { arrayOf(it.toString()) }, null, null, "semester_id, number"
    ).use { cursor -> buildList {
        while (cursor.moveToNext()) add(CoursePeriod(cursor.getInt(1), cursor.getInt(2), cursor.getInt(3), cursor.getLong(0)))
    } }

    fun saveSemester(semester: Semester, periods: List<CoursePeriod>): Long = transaction {
        semester.validate(); Timetable.validatePeriods(periods)
        if (semester.id > 0) {
            require(getSemester(semester.id) != null) { "学期不存在" }
            require(getCourses().filter { it.semesterId == semester.id }.all { course -> course.weeks.all { it <= semester.totalWeeks } }) { "现有课程包含较后的周次，请先修改课程再缩短学期" }
        }
        val id = if (semester.id == 0L) writableDatabase.insertOrThrow("semesters", null, semester.toContentValues())
            else semester.id.also { writableDatabase.update("semesters", semester.toContentValues(), "id = ?", arrayOf(it.toString())) }
        replacePeriods(id, periods.map { it.copy(semesterId = id) })
        id
    }

    private fun replacePeriods(id: Long, periods: List<CoursePeriod>) {
        Timetable.validatePeriods(periods)
        writableDatabase.delete("periods", "semester_id = ?", arrayOf(id.toString()))
        periods.forEach { writableDatabase.insertOrThrow("periods", null, it.copy(semesterId = id).toContentValues()) }
    }

    fun deleteSemester(id: Long) = transaction {
        require(getSemesters().size > 1) { "至少保留一个学期" }
        writableDatabase.delete("courses", "semester_id = ?", arrayOf(id.toString()))
        writableDatabase.delete("periods", "semester_id = ?", arrayOf(id.toString()))
        writableDatabase.delete("semesters", "id = ?", arrayOf(id.toString()))
    }

    fun importCourses(semesterId: Long, courses: List<Course>, replace: Boolean): Int = transaction {
        val semester = getSemester(semesterId) ?: error("目标学期已不存在，请重新导入")
        require(courses.isNotEmpty() && courses.size <= 2000) { "没有可导入的课程或超过 2000 条限制" }
        courses.forEach { it.validate(semester) }
        if (replace) writableDatabase.delete("courses", "semester_id = ?", arrayOf(semesterId.toString()))
        val existing = getCourses().filter { it.semesterId == semesterId }.map { it.importKey() }.toMutableSet()
        var inserted = 0
        courses.forEach { if (existing.add(it.importKey())) { insertCourse(it.copy(id = 0)); inserted++ } }
        inserted
    }

    private fun Semester.toContentValues() = ContentValues().apply {
        put("name", name.trim()); put("start_date", startDate); put("total_weeks", totalWeeks)
    }
    private fun CoursePeriod.toContentValues() = ContentValues().apply {
        put("semester_id", semesterId); put("number", number); put("start_minute", startMinute); put("end_minute", endMinute)
    }

    companion object {
        private const val DATABASE_NAME = "suixinji.db"
        private const val DATABASE_VERSION = 4
    }
}
