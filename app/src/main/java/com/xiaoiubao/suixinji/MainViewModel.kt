package com.xiaoiubao.suixinji

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoiubao.suixinji.data.BackupService
import com.xiaoiubao.suixinji.data.Course
import com.xiaoiubao.suixinji.data.EventDatabase
import com.xiaoiubao.suixinji.data.EventNote
import com.xiaoiubao.suixinji.data.ImportService
import com.xiaoiubao.suixinji.reminder.CourseReminderScheduler
import com.xiaoiubao.suixinji.reminder.ReminderScheduler
import com.xiaoiubao.suixinji.settings.AppSettings
import com.xiaoiubao.suixinji.widget.EventWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class EventFilter {
    ALL, UPCOMING, COMPLETED
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = EventDatabase(application)
    private val importer = ImportService(application)
    private val backupService = BackupService(application)
    private val settings = AppSettings(application)

    private val _events = MutableStateFlow<List<EventNote>>(emptyList())
    val events: StateFlow<List<EventNote>> = _events.asStateFlow()

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { refreshInternal() }
    }

    fun save(note: EventNote) {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = if (note.id == 0L) {
                val id = db.insert(note)
                note.copy(id = id)
            } else {
                db.update(note)
                note
            }
            ReminderScheduler.schedule(getApplication(), saved)
            refreshInternal()
            EventWidgetProvider.updateAll(getApplication())
        }
    }

    fun delete(note: EventNote) {
        viewModelScope.launch(Dispatchers.IO) {
            ReminderScheduler.cancel(getApplication(), note.id)
            db.delete(note.id)
            refreshInternal()
            EventWidgetProvider.updateAll(getApplication())
        }
    }

    fun toggleCompleted(note: EventNote) {
        save(note.copy(completed = !note.completed))
    }

    fun saveCourse(course: Course) {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = if (course.id == 0L) {
                val id = db.insertCourse(course)
                course.copy(id = id)
            } else {
                db.updateCourse(course)
                course
            }
            CourseReminderScheduler.schedule(getApplication(), saved)
            refreshInternal()
            EventWidgetProvider.updateAll(getApplication())
        }
    }

    fun deleteCourse(course: Course) {
        viewModelScope.launch(Dispatchers.IO) {
            CourseReminderScheduler.cancel(getApplication(), course.id)
            db.deleteCourse(course.id)
            refreshInternal()
            EventWidgetProvider.updateAll(getApplication())
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val result = importer.importInto(db, uri)
                scheduleAllReminders()
                result.message
            }.onSuccess { _importMessage.value = it }
                .onFailure { _importMessage.value = "导入失败：${it.message ?: "文件无法读取"}" }
            refreshInternal()
            EventWidgetProvider.updateAll(getApplication())
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _importMessage.value = runCatching { backupService.exportEventsCsv(db, uri) }
                .getOrElse { "CSV 导出失败：${it.message ?: "无法写入文件"}" }
        }
    }

    fun createBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _importMessage.value = runCatching { backupService.createBackup(db, settings, uri) }
                .getOrElse { "备份失败：${it.message ?: "无法写入备份"}" }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldEvents = db.getAll()
            val oldCourses = db.getCourses()
            val result = runCatching {
                oldEvents.forEach { ReminderScheduler.cancel(getApplication(), it.id) }
                oldCourses.forEach { CourseReminderScheduler.cancel(getApplication(), it.id) }
                backupService.restoreBackup(db, settings, uri)
            }
            if (result.isSuccess) scheduleAllReminders()
            _importMessage.value = result.getOrElse { "恢复失败：${it.message ?: "备份文件无效"}" }
            refreshInternal()
            EventWidgetProvider.updateAll(getApplication())
        }
    }

    fun clearImportMessage() {
        _importMessage.value = null
    }

    private fun scheduleAllReminders() {
        db.getAll()
            .filter { it.reminderEnabled && !it.completed && it.eventTime != null }
            .forEach { ReminderScheduler.schedule(getApplication(), it) }
        db.getCourses()
            .filter { it.reminderEnabled }
            .forEach { CourseReminderScheduler.schedule(getApplication(), it) }
    }

    private fun refreshInternal() {
        _events.value = db.getAll()
        _courses.value = db.getCourses()
    }

    override fun onCleared() {
        db.close()
        super.onCleared()
    }
}
