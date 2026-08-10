package com.xiaoiubao.suixinji

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoiubao.suixinji.data.Course
import com.xiaoiubao.suixinji.data.EventDatabase
import com.xiaoiubao.suixinji.data.EventNote
import com.xiaoiubao.suixinji.data.ImportService
import com.xiaoiubao.suixinji.reminder.ReminderScheduler
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
            if (course.id == 0L) db.insertCourse(course) else db.updateCourse(course)
            refreshInternal()
            EventWidgetProvider.updateAll(getApplication())
        }
    }

    fun deleteCourse(course: Course) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteCourse(course.id)
            refreshInternal()
            EventWidgetProvider.updateAll(getApplication())
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = importer.importInto(db, uri)
            db.getAll().filter { it.reminderEnabled && !it.completed && it.eventTime != null }.forEach {
                ReminderScheduler.schedule(getApplication(), it)
            }
            _importMessage.value = result.message
            refreshInternal()
            EventWidgetProvider.updateAll(getApplication())
        }
    }

    fun clearImportMessage() {
        _importMessage.value = null
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
