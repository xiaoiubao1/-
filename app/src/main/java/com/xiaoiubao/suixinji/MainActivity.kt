package com.xiaoiubao.suixinji

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.xiaoiubao.suixinji.data.EventNote
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.refresh()
        val targetEventId = intent.getLongExtra(EXTRA_EVENT_ID, 0L)

        setContent {
            MaterialTheme {
                SuixinjiScreen(
                    viewModel = viewModel,
                    targetEventId = targetEventId
                )
            }
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
    }
}

@Composable
private fun SuixinjiScreen(
    viewModel: MainViewModel,
    targetEventId: Long
) {
    val context = LocalContext.current
    val events by viewModel.events.collectAsState()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(EventFilter.ALL) }
    var editing by remember { mutableStateOf<EventNote?>(null) }
    var handledTarget by remember(targetEventId) { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val visibleEvents = remember(events, query, filter) {
        val normalized = query.trim().lowercase()
        events.filter { note ->
            val matchesQuery = normalized.isBlank() ||
                note.title.lowercase().contains(normalized) ||
                note.details.lowercase().contains(normalized) ||
                note.location.lowercase().contains(normalized)

            val matchesFilter = when (filter) {
                EventFilter.ALL -> true
                EventFilter.UPCOMING -> !note.completed
                EventFilter.COMPLETED -> note.completed
            }
            matchesQuery && matchesFilter
        }
    }

    LaunchedEffect(events, targetEventId) {
        if (!handledTarget && targetEventId > 0 && events.isNotEmpty()) {
            events.firstOrNull { it.id == targetEventId }?.let {
                editing = it
                handledTarget = true
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = EventNote() }) {
                Icon(Icons.Default.Add, contentDescription = "新建记录")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "随心记",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "记下事情，也记住时间与地点",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索标题、内容或地点") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清空")
                        }
                    }
                }
            )

            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == EventFilter.ALL,
                    onClick = { filter = EventFilter.ALL },
                    label = { Text("全部") }
                )
                FilterChip(
                    selected = filter == EventFilter.UPCOMING,
                    onClick = { filter = EventFilter.UPCOMING },
                    label = { Text("待办") }
                )
                FilterChip(
                    selected = filter == EventFilter.COMPLETED,
                    onClick = { filter = EventFilter.COMPLETED },
                    label = { Text("已完成") }
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()

            if (visibleEvents.isEmpty()) {
                EmptyState(
                    hasSearch = query.isNotBlank() || filter != EventFilter.ALL,
                    onAdd = { editing = EventNote() }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { Spacer(Modifier.height(2.dp)) }
                    items(visibleEvents, key = { it.id }) { note ->
                        EventCard(
                            note = note,
                            onEdit = { editing = note },
                            onToggleCompleted = { viewModel.toggleCompleted(note) },
                            onDelete = { viewModel.delete(note) }
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }

    editing?.let { note ->
        EventEditorDialog(
            note = note,
            onDismiss = { editing = null },
            onSave = { updated ->
                if (
                    updated.reminderEnabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.save(updated)
                editing = null
            }
        )
    }
}

@Composable
private fun EmptyState(
    hasSearch: Boolean,
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (hasSearch) "没有找到符合条件的记录" else "还没有记录",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (hasSearch) "换个关键词或筛选条件试试" else "先记下一件现在不想忘的事吧",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!hasSearch) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("新建记录")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventCard(
    note: EventNote,
    onEdit: () -> Unit,
    onToggleCompleted: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (note.completed) TextDecoration.LineThrough else null
                    )
                    if (note.details.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = note.details,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onToggleCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = if (note.completed) "恢复待办" else "标记完成"
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }

            if (note.eventTime != null || note.location.isNotBlank() || note.reminderEnabled) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    note.eventTime?.let {
                        AssistChip(
                            onClick = onEdit,
                            label = { Text(formatTime(it)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Event,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                    if (note.location.isNotBlank()) {
                        AssistChip(
                            onClick = onEdit,
                            label = { Text(note.location, maxLines = 1) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                    if (note.reminderEnabled && !note.completed) {
                        AssistChip(
                            onClick = onEdit,
                            label = { Text("已提醒") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventEditorDialog(
    note: EventNote,
    onDismiss: () -> Unit,
    onSave: (EventNote) -> Unit
) {
    val context = LocalContext.current
    var title by remember(note.id) { mutableStateOf(note.title) }
    var details by remember(note.id) { mutableStateOf(note.details) }
    var location by remember(note.id) { mutableStateOf(note.location) }
    var eventTime by remember(note.id) { mutableStateOf(note.eventTime) }
    var reminderEnabled by remember(note.id) { mutableStateOf(note.reminderEnabled) }

    fun baseCalendar(): Calendar = Calendar.getInstance().apply {
        timeInMillis = eventTime ?: (System.currentTimeMillis() + 60 * 60 * 1000)
    }

    fun pickDate() {
        val current = baseCalendar()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val updated = baseCalendar().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                eventTime = updated.timeInMillis
            },
            current.get(Calendar.YEAR),
            current.get(Calendar.MONTH),
            current.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun pickTime() {
        val current = baseCalendar()
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val updated = baseCalendar().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                eventTime = updated.timeInMillis
            },
            current.get(Calendar.HOUR_OF_DAY),
            current.get(Calendar.MINUTE),
            true
        ).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (note.id == 0L) "新建记录" else "编辑记录",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("事件标题 *") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("详细内容") },
                    minLines = 3,
                    maxLines = 6
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("地点 / 位置") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    singleLine = true
                )

                Spacer(Modifier.height(16.dp))
                Text("时间", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                if (eventTime == null) {
                    Button(
                        onClick = {
                            eventTime = System.currentTimeMillis() + 60 * 60 * 1000
                            pickDate()
                        }
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("设置事件时间")
                    }
                } else {
                    Text(
                        text = formatTime(eventTime!!),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = ::pickDate) { Text("修改日期") }
                        TextButton(onClick = ::pickTime) { Text("修改时间") }
                        TextButton(
                            onClick = {
                                eventTime = null
                                reminderEnabled = false
                            }
                        ) { Text("清除") }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (reminderEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("到点通知", fontWeight = FontWeight.SemiBold)
                        Text(
                            "系统将在设定时间附近发送提醒",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { enabled ->
                            reminderEnabled = enabled
                            if (enabled && eventTime == null) {
                                eventTime = System.currentTimeMillis() + 60 * 60 * 1000
                            }
                        }
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = title.isNotBlank(),
                        onClick = {
                            onSave(
                                note.copy(
                                    title = title.trim(),
                                    details = details.trim(),
                                    location = location.trim(),
                                    eventTime = eventTime,
                                    reminderEnabled = reminderEnabled && eventTime != null
                                )
                            )
                        }
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

private fun formatTime(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
