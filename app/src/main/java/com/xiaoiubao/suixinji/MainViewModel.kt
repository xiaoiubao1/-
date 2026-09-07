package com.xiaoiubao.suixinji

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoiubao.suixinji.data.*
import com.xiaoiubao.suixinji.reminder.*
import com.xiaoiubao.suixinji.settings.AppSettings
import com.xiaoiubao.suixinji.widget.EventWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.withLock as withDataLock

enum class EventFilter { ALL, UPCOMING, COMPLETED }

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val importer = ImportService(application)
    private val backupService = BackupService(application)
    private val settings = AppSettings(application)
    private val courseImporter = CourseImportService(application)
    private val gate = Mutex()
    private val _events = MutableStateFlow<List<EventNote>>(emptyList())
    val events = _events.asStateFlow()
    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses = _courses.asStateFlow()
    private val _semesters = MutableStateFlow<List<Semester>>(emptyList())
    val semesters = _semesters.asStateFlow()
    private val _periods = MutableStateFlow<List<CoursePeriod>>(emptyList())
    val periods = _periods.asStateFlow()
    private val _activeSemesterId = MutableStateFlow(settings.activeSemesterId)
    val activeSemesterId = _activeSemesterId.asStateFlow()
    private val _coursePreview = MutableStateFlow<CourseImportPreview?>(null)
    val coursePreview = _coursePreview.asStateFlow()
    var editingSemester by mutableStateOf<SemesterDraft?>(null)
    var showSchoolBrowser by mutableStateOf(false)
    var schoolBrowserUrl by mutableStateOf("")
    var browserSessionStarted = false
    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage = _importMessage.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    // Keep drafts out of Bundle limits (notes can be long), and retain them across rotation.
    var editingEvent by mutableStateOf<EventNote?>(null)
    var editingCourse by mutableStateOf<Course?>(null)

    private fun operation(label: String, showBusy: Boolean = true, lockData: Boolean = true, onSuccess: () -> Unit = {}, block: (EventDatabase) -> String?) {
        viewModelScope.launch {
            gate.withLock {
                if (showBusy) _busy.value = true
                try {
                    val message = withContext(Dispatchers.IO) {
                        fun execute(): String? = EventDatabase(getApplication()).use { db ->
                            val result = block(db)
                            _events.value = db.getAll()
                            _courses.value = db.getCourses()
                            _semesters.value = db.getSemesters()
                            _periods.value = db.getPeriods()
                            if (_semesters.value.none { it.id == settings.activeSemesterId }) settings.activeSemesterId = _semesters.value.first().id
                            _activeSemesterId.value = settings.activeSemesterId
                            result
                        }
                        if (lockData) DataAccess.lock.withDataLock { execute() } else execute()
                    }
                    onSuccess()
                    if (message != null) _importMessage.value = message
                    EventWidgetProvider.updateAll(getApplication())
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { _importMessage.value = "$label 失败：${e.message ?: "请稍后重试"}" }
                finally { if (showBusy) _busy.value = false }
            }
        }
    }

    fun refresh() = operation("读取", showBusy = false) { null }

    fun rescheduleReminders() = operation("重建提醒", showBusy = false) { db ->
        scheduleAll(db); null
    }

    fun save(note: EventNote) = operation("保存", onSuccess = {
        if (editingEvent?.id == note.id) editingEvent = null
    }) { db ->
        require(note.title.isNotBlank()) { "请填写标题" }
        val saved = if (note.id == 0L) note.copy(id = db.insert(note)) else note.also(db::update)
        reminderResult { ReminderScheduler.schedule(getApplication(), saved) } ?: when {
            saved.reminderEnabled && !saved.completed && (saved.eventTime == null || saved.eventTime <= System.currentTimeMillis()) ->
                "记录已保存；请设置未来时间以启用提醒"
            saved.reminderEnabled -> permissionHint()
            else -> null
        }
    }

    fun delete(note: EventNote) = operation("删除") { db ->
        db.delete(note.id)
        reminderResult { ReminderScheduler.cancel(getApplication(), note.id) }
    }

    fun toggleCompleted(note: EventNote) { save(note.copy(completed = !note.completed)) }

    fun saveCourse(course: Course) = operation("保存课程", onSuccess = {
        if (editingCourse?.id == course.id) editingCourse = null
    }) { db ->
        course.validate(db.getSemester(course.semesterId) ?: error("课程所属学期不存在"))
        val saved = if (course.id == 0L) course.copy(id = db.insertCourse(course)) else course.also(db::updateCourse)
        reminderResult { CourseReminderScheduler.schedule(getApplication(), saved) } ?:
            if (saved.reminderEnabled) permissionHint() else null
    }

    fun deleteCourse(course: Course) = operation("删除课程", onSuccess = { editingCourse = null }) { db ->
        db.deleteCourse(course.id)
        reminderResult { CourseReminderScheduler.cancel(getApplication(), course.id) }
    }

    fun importFromUri(uri: Uri) = operation("导入", lockData = false) { db ->
        val result = importer.importInto(db, uri)
        result.message + (reminderResult { scheduleAll(db) }?.let { "；$it" } ?: "")
    }
    fun exportCsv(uri: Uri) = operation("CSV 导出", lockData = false) { backupService.exportEventsCsv(it, uri) }
    fun createBackup(uri: Uri) = operation("备份", lockData = false) { backupService.createBackup(it, settings, uri) }

    fun restoreBackup(uri: Uri) = operation("恢复", lockData = false, onSuccess = { editingEvent = null; editingCourse = null; editingSemester = null; _coursePreview.value = null }) { db ->
        val oldEvents = db.getAll()
        val oldCourses = db.getCourses()
        val message = backupService.restoreBackup(db, settings, uri)
        // This point is reached only after validation and the data commit succeed.
        val warning = reminderResult {
            DataAccess.lock.withDataLock {
                oldEvents.forEach { ReminderScheduler.cancel(getApplication(), it.id) }
                oldCourses.forEach { CourseReminderScheduler.cancel(getApplication(), it.id) }
                scheduleAll(db)
            }
        }
        message + (warning?.let { "；$it" } ?: "")
    }

    fun selectSemester(id: Long) = operation("切换学期") { db ->
        require(db.getSemester(id) != null) { "学期不存在" }
        settings.activeSemesterId = id
        reminderResult { scheduleAll(db) }
    }

    fun saveSemester(draft: SemesterDraft) = operation("保存学期", onSuccess = { editingSemester = null }) { db ->
        val (semester, periods) = draft.parse()
        val id = db.saveSemester(semester, periods)
        settings.activeSemesterId = id
        reminderResult { scheduleAll(db) } ?: "学期和作息已保存。已有课程保持原来的钟点时间；需要调整时，请在课程中重新选择节次。"
    }

    fun deleteSemester(id: Long) = operation("删除学期", onSuccess = { editingSemester = null }) { db ->
        val old = db.getCourses().filter { it.semesterId == id }
        db.deleteSemester(id)
        if (settings.activeSemesterId == id) settings.activeSemesterId = db.getSemesters().first().id
        reminderResult {
            old.forEach { CourseReminderScheduler.cancel(getApplication(), it.id) }
            scheduleAll(db)
        }
    }

    fun previewCourseFile(uri: Uri, semesterId: Long) = operation("读取课程", lockData = false) { db ->
        val term = db.getSemester(semesterId) ?: error("请选择学期")
        _coursePreview.value = previewStats(courseImporter.read(uri, term, db.getPeriods(semesterId)), db)
        null
    }

    fun previewSchoolPage(encoded: String, semesterId: Long) = operation("识别网页课表", lockData = false) { db ->
        require(encoded.length <= 8 * CourseImportService.MAX_BYTES) { "页面过大" }
        val decoded = org.json.JSONTokener(encoded).nextValue() as? String ?: error("无法读取页面，请等待课表加载完成")
        val payload = org.json.JSONObject(decoded)
        require(!payload.has("error")) { payload.optString("error") }
        val term = db.getSemester(semesterId) ?: error("请选择学期")
        val preview = CourseImportService.parseHtml(payload.getString("html"), term, db.getPeriods(semesterId))
        val frameWarning = if (payload.optBoolean("unreadableFrames")) listOf("部分跨域框架无法读取，请在预览中检查是否缺课；可打开课表的独立网址后重试。") else emptyList()
        _coursePreview.value = previewStats(preview.copy(warnings = preview.warnings + frameWarning,
            skippedRows = preview.skippedRows + if (frameWarning.isEmpty()) 0 else 1), db)
        null
    }

    private fun previewStats(preview: CourseImportPreview, db: EventDatabase): CourseImportPreview {
        val existing = db.getCourses().filter { it.semesterId == preview.semesterId }
        val keys = existing.map { it.importKey() }.toSet()
        val weekly = db.getSemester(preview.semesterId)?.startDate.isNullOrBlank()
        val all = (existing + preview.courses).distinctBy { it.importKey() }.groupBy { it.dayOfWeek }
        return preview.copy(warnings = preview.warnings + if (weekly) listOf("当前学期未设置第一周日期，课程会每周重复；请在导入后设置学期日期，使周次生效。") else emptyList(), duplicateCount = preview.courses.count { it.importKey() in keys },
            conflictCount = preview.courses.count { course -> all[course.dayOfWeek].orEmpty().any { it.importKey() != course.importKey() && Timetable.conflicts(course, it, weekly) } })
    }

    fun discardCoursePreview() { _coursePreview.value = null }

    fun confirmCourseImport(preview: CourseImportPreview, selected: Set<Int>, replace: Boolean) = operation("导入课程", onSuccess = { _coursePreview.value = null }) { db ->
        require(_coursePreview.value === preview) { "预览已过期，请重新读取" }
        require(!replace || preview.skippedRows == 0) { "存在未识别内容时不能覆盖课表" }
        val chosen = preview.courses.filterIndexed { index, _ -> index in selected }
        require(chosen.isNotEmpty()) { "请至少选择一条课程" }
        val old = if (replace) db.getCourses().filter { it.semesterId == preview.semesterId } else emptyList()
        val inserted = db.importCourses(preview.semesterId, chosen, replace)
        val warning = reminderResult {
            old.forEach { CourseReminderScheduler.cancel(getApplication(), it.id) }
            scheduleAll(db)
        }
        "已导入 $inserted 条课程安排，跳过 ${chosen.size - inserted} 条重复安排。导入课程默认关闭提醒，可在课程详情开启。" + (warning?.let { "；$it" } ?: "")
    }

    fun exportCourses(uri: Uri, semesterId: Long) = operation("导出课程", lockData = false) { db ->
        val courses = DataAccess.lock.withDataLock { db.getCourses().filter { it.semesterId == semesterId } }
        courseImporter.export(uri, courses)
    }

    fun clearImportMessage() { _importMessage.value = null }
    private fun scheduleAll(db: EventDatabase) = DataAccess.lock.withDataLock {
        db.getAll().forEach { ReminderScheduler.schedule(getApplication(), it) }
        db.getCourses().forEach { CourseReminderScheduler.schedule(getApplication(), it) }
    }
    private fun reminderResult(block: () -> Unit): String? = try { block(); null }
        catch (e: Exception) { "数据已保存，提醒更新失败，请重新打开应用重试：${e.message.orEmpty()}" }
    private fun permissionHint(): String? = when {
        !NotificationTester.canNotify(getApplication()) -> "已保存，请允许通知后接收提醒"
        !ReminderAlarms.canScheduleExact(getApplication()) -> "已保存；当前提醒可能延迟，请到“我的”开启准时提醒权限"
        else -> null
    }
}
