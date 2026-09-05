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
    private val gate = Mutex()
    private val _events = MutableStateFlow<List<EventNote>>(emptyList())
    val events = _events.asStateFlow()
    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses = _courses.asStateFlow()
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
        require(course.name.isNotBlank() && course.endMinute > course.startMinute) { "请检查课程名称和起止时间" }
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

    fun restoreBackup(uri: Uri) = operation("恢复", lockData = false, onSuccess = { editingEvent = null; editingCourse = null }) { db ->
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
