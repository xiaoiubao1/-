package com.xiaoiubao.suixinji

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoiubao.suixinji.data.EventDatabase
import com.xiaoiubao.suixinji.data.EventNote
import com.xiaoiubao.suixinji.reminder.ReminderScheduler
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

    private val _events = MutableStateFlow<List<EventNote>>(emptyList())
    val events: StateFlow<List<EventNote>> = _events.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _events.value = db.getAll()
        }
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
            _events.value = db.getAll()
        }
    }

    fun delete(note: EventNote) {
        viewModelScope.launch(Dispatchers.IO) {
            ReminderScheduler.cancel(getApplication(), note.id)
            db.delete(note.id)
            _events.value = db.getAll()
        }
    }

    fun toggleCompleted(note: EventNote) {
        save(note.copy(completed = !note.completed))
    }

    override fun onCleared() {
        db.close()
        super.onCleared()
    }
}
