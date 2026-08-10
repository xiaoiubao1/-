package com.xiaoiubao.suixinji

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.xiaoiubao.suixinji.data.Course
import com.xiaoiubao.suixinji.data.EventNote
import com.xiaoiubao.suixinji.reminder.NotificationTester
import com.xiaoiubao.suixinji.settings.AppSettings
import com.xiaoiubao.suixinji.settings.ThemePreset
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.refresh()
        val targetEventId = intent.getLongExtra(EXTRA_EVENT_ID, 0L)
        val settings = AppSettings(this)

        setContent {
            SuixinjiApp(
                viewModel = viewModel,
                targetEventId = targetEventId,
                settings = settings
            )
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
    }
}

private enum class MainSection { NOTES, TIMETABLE, SETTINGS }

@Composable
private fun SuixinjiApp(
    viewModel: MainViewModel,
    targetEventId: Long,
    settings: AppSettings
) {
    val context = LocalContext.current
    val events by viewModel.events.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val importMessage by viewModel.importMessage.collectAsState()

    var section by remember { mutableStateOf(MainSection.NOTES) }
    var editingEvent by remember { mutableStateOf<EventNote?>(null) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }
    var theme by remember { mutableStateOf(settings.theme) }
    var wallpaper by remember { mutableStateOf(settings.wallpaper) }
    var handledTarget by remember(targetEventId) { mutableStateOf(false) }
    var pendingNotificationTest by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingNotificationTest) NotificationTester.send(context)
        pendingNotificationTest = false
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            wallpaper = it.toString()
            settings.wallpaper = wallpaper
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            viewModel.importFromUri(it)
        }
    }

    LaunchedEffect(events, targetEventId) {
        if (!handledTarget && targetEventId > 0 && events.isNotEmpty()) {
            events.firstOrNull { it.id == targetEventId }?.let {
                section = MainSection.NOTES
                editingEvent = it
                handledTarget = true
            }
        }
    }

    MaterialTheme(colorScheme = colorSchemeFor(theme)) {
        Box(Modifier.fillMaxSize()) {
            WallpaperLayer(wallpaper)
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background.copy(
                    alpha = if (wallpaper == AppSettings.WALLPAPER_NONE) 1f else 0.88f
                )
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
                            NavigationBarItem(
                                selected = section == MainSection.NOTES,
                                onClick = { section = MainSection.NOTES },
                                icon = { Icon(Icons.Default.List, contentDescription = null) },
                                label = { Text("记录") }
                            )
                            NavigationBarItem(
                                selected = section == MainSection.TIMETABLE,
                                onClick = { section = MainSection.TIMETABLE },
                                icon = { Icon(Icons.Default.School, contentDescription = null) },
                                label = { Text("课程表") }
                            )
                            NavigationBarItem(
                                selected = section == MainSection.SETTINGS,
                                onClick = { section = MainSection.SETTINGS },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("设置") }
                            )
                        }
                    },
                    floatingActionButton = {
                        when (section) {
                            MainSection.NOTES -> FloatingActionButton(onClick = { editingEvent = EventNote() }) {
                                Icon(Icons.Default.Add, contentDescription = "新建记录")
                            }
                            MainSection.TIMETABLE -> FloatingActionButton(onClick = { editingCourse = Course() }) {
                                Icon(Icons.Default.Add, contentDescription = "添加课程")
                            }
                            MainSection.SETTINGS -> Unit
                        }
                    }
                ) { innerPadding ->
                    when (section) {
                        MainSection.NOTES -> NotesScreen(
                            modifier = Modifier.padding(innerPadding),
                            events = events,
                            onEdit = { editingEvent = it },
                            onToggleCompleted = viewModel::toggleCompleted,
                            onDelete = viewModel::delete,
                            onAdd = { editingEvent = EventNote() }
                        )
                        MainSection.TIMETABLE -> TimetableScreen(
                            modifier = Modifier.padding(innerPadding),
                            courses = courses,
                            onEdit = { editingCourse = it },
                            onDelete = viewModel::deleteCourse,
                            onAdd = { editingCourse = Course() }
                        )
                        MainSection.SETTINGS -> SettingsScreen(
                            modifier = Modifier.padding(innerPadding),
                            theme = theme,
                            wallpaper = wallpaper,
                            onThemeChange = {
                                theme = it
                                settings.theme = it
                            },
                            onNoWallpaper = {
                                wallpaper = AppSettings.WALLPAPER_NONE
                                settings.wallpaper = wallpaper
                            },
                            onBuiltinWallpaper = {
                                wallpaper = AppSettings.WALLPAPER_BUILTIN
                                settings.wallpaper = wallpaper
                            },
                            onPickWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
                            onImport = {
                                importPicker.launch(arrayOf("text/csv", "application/json", "text/plain", "*/*"))
                            },
                            onTestNotification = {
                                if (NotificationTester.canNotify(context)) {
                                    NotificationTester.send(context)
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    pendingNotificationTest = true
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    editingEvent?.let { note ->
        EventEditorDialog(
            note = note,
            onDismiss = { editingEvent = null },
            onSave = { updated ->
                if (
                    updated.reminderEnabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.save(updated)
                editingEvent = null
            }
        )
    }

    editingCourse?.let { course ->
        CourseEditorDialog(
            course = course,
            onDismiss = { editingCourse = null },
            onSave = {
                viewModel.saveCourse(it)
                editingCourse = null
            }
        )
    }

    importMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearImportMessage,
            confirmButton = {
                TextButton(onClick = viewModel::clearImportMessage) { Text("知道了") }
            },
            title = { Text("导入结果") },
            text = { Text(message) }
        )
    }
}

@Composable
private fun WallpaperLayer(wallpaper: String) {
    when {
        wallpaper == AppSettings.WALLPAPER_NONE -> Unit
        wallpaper == AppSettings.WALLPAPER_BUILTIN -> Image(
            painter = painterResource(R.drawable.chibi_wallpaper),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        wallpaper.isNotBlank() -> UriImage(
            uriString = wallpaper,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun UriImage(
    uriString: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val bitmap = remember(uriString) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

@Composable
private fun NotesScreen(
    modifier: Modifier,
    events: List<EventNote>,
    onEdit: (EventNote) -> Unit,
    onToggleCompleted: (EventNote) -> Unit,
    onDelete: (EventNote) -> Unit,
    onAdd: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(EventFilter.ALL) }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("随心记", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "记录、图片、提醒，都放在一个地方",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜索标题、内容或地点") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Default.Close, contentDescription = "清空")
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filter == EventFilter.ALL, { filter = EventFilter.ALL }, { Text("全部") })
            FilterChip(filter == EventFilter.UPCOMING, { filter = EventFilter.UPCOMING }, { Text("待办") })
            FilterChip(filter == EventFilter.COMPLETED, { filter = EventFilter.COMPLETED }, { Text("已完成") })
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()

        if (visibleEvents.isEmpty()) {
            EmptyState(
                title = if (query.isNotBlank() || filter != EventFilter.ALL) "没有找到记录" else "还没有记录",
                subtitle = if (query.isNotBlank() || filter != EventFilter.ALL) "换个关键词试试" else "先记下一件不想忘的事吧",
                button = if (query.isBlank() && filter == EventFilter.ALL) "新建记录" else null,
                onClick = onAdd
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
                        onEdit = { onEdit(note) },
                        onToggleCompleted = { onToggleCompleted(note) },
                        onDelete = { onDelete(note) }
                    )
                }
                item { Spacer(Modifier.height(96.dp)) }
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
    ) {
        Column(Modifier.padding(16.dp)) {
            if (note.imageUri.isNotBlank()) {
                UriImage(
                    note.imageUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        note.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (note.completed) TextDecoration.LineThrough else null
                    )
                    if (note.details.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            note.details,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onToggleCompleted) {
                    Icon(Icons.Default.Check, contentDescription = if (note.completed) "恢复待办" else "标记完成")
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "编辑") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "删除") }
            }

            if (note.eventTime != null || note.location.isNotBlank() || (note.reminderEnabled && !note.completed)) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    note.eventTime?.let {
                        AssistChip(
                            onClick = onEdit,
                            label = { Text(formatTime(it)) },
                            leadingIcon = { Icon(Icons.Default.Event, null, Modifier.size(18.dp)) }
                        )
                    }
                    if (note.location.isNotBlank()) {
                        AssistChip(
                            onClick = onEdit,
                            label = { Text(note.location, maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)) }
                        )
                    }
                    if (note.reminderEnabled && !note.completed) {
                        AssistChip(
                            onClick = onEdit,
                            label = { Text("已开启提醒") },
                            leadingIcon = { Icon(Icons.Default.Notifications, null, Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimetableScreen(
    modifier: Modifier,
    courses: List<Course>,
    onEdit: (Course) -> Unit,
    onDelete: (Course) -> Unit,
    onAdd: () -> Unit
) {
    var selectedDay by remember { mutableStateOf(currentWeekday()) }
    val dayCourses = remember(courses, selectedDay) {
        courses.filter { it.dayOfWeek == selectedDay }.sortedBy { it.startMinute }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("课程表", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("按星期安排课程，桌面小组件也会显示今天的下一节课", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                WeekdayChips(selectedDay = selectedDay, onSelected = { selectedDay = it })
                Spacer(Modifier.height(8.dp))
            }
            if (dayCourses.isEmpty()) {
                item {
                    EmptyState(
                        title = "${weekdayName(selectedDay)}还没有课程",
                        subtitle = "添加课程后会按时间自动排序",
                        button = "添加课程",
                        onClick = onAdd
                    )
                }
            } else {
                items(dayCourses, key = { it.id }) { course ->
                    CourseCard(course, onEdit = { onEdit(course) }, onDelete = { onDelete(course) })
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayChips(selectedDay: Int, onSelected: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { day ->
            FilterChip(
                selected = selectedDay == day,
                onClick = { onSelected(day) },
                label = { Text(weekdayShort(day)) }
            )
        }
    }
}

@Composable
private fun CourseCard(course: Course, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${formatMinute(course.startMinute)} - ${formatMinute(course.endMinute)}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val meta = listOf(course.teacher, course.location).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (course.note.isNotBlank()) Text(course.note, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "编辑课程") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "删除课程") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    modifier: Modifier,
    theme: ThemePreset,
    wallpaper: String,
    onThemeChange: (ThemePreset) -> Unit,
    onNoWallpaper: () -> Unit,
    onBuiltinWallpaper: () -> Unit,
    onPickWallpaper: () -> Unit,
    onImport: () -> Unit,
    onTestNotification: () -> Unit
) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("个性化与工具", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("主题、壁纸、导入和通知测试都在这里", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingsCard(Icons.Default.Palette, "主题") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = theme == preset,
                            onClick = { onThemeChange(preset) },
                            label = { Text(preset.title) }
                        )
                    }
                }
            }
        }
        item {
            SettingsCard(Icons.Default.Wallpaper, "壁纸") {
                Text(
                    when (wallpaper) {
                        AppSettings.WALLPAPER_NONE -> "当前：无壁纸"
                        AppSettings.WALLPAPER_BUILTIN -> "当前：Q版内置壁纸"
                        else -> "当前：自定义图片"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onNoWallpaper) { Text("无壁纸") }
                    OutlinedButton(onClick = onBuiltinWallpaper) { Text("Q版壁纸") }
                    Button(onClick = onPickWallpaper) {
                        Icon(Icons.Default.Photo, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("选择图片")
                    }
                }
            }
        }
        item {
            SettingsCard(Icons.Default.NotificationsActive, "通知测试") {
                Text("点一下立即发送测试通知，用来确认系统权限和通知渠道是否正常。")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onTestNotification) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("发送测试通知")
                }
            }
        }
        item {
            SettingsCard(Icons.Default.UploadFile, "导入记录") {
                Text("支持 CSV、JSON 和 TXT。TXT 每一行会作为一条新记录；CSV 支持中英文表头。")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onImport) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("选择文件导入")
                }
            }
        }
        item {
            SettingsCard(Icons.Default.CalendarMonth, "桌面小组件") {
                Text("长按安卓桌面 → 小组件 → 随心记，把“随心记概览”拖到桌面。它会显示下一条待办和今天下一节课程。")
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, button: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (button != null) {
            Spacer(Modifier.height(14.dp))
            Button(onClick = onClick) { Text(button) }
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
    var imageUri by remember(note.id) { mutableStateOf(note.imageUri) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            imageUri = it.toString()
        }
    }

    fun baseCalendar(): Calendar = Calendar.getInstance().apply {
        timeInMillis = eventTime ?: (System.currentTimeMillis() + 60 * 60 * 1000)
    }

    fun pickDate() {
        val current = baseCalendar()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                eventTime = baseCalendar().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            },
            current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun pickTime() {
        val current = baseCalendar()
        TimePickerDialog(
            context,
            { _, hour, minute ->
                eventTime = baseCalendar().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            },
            current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true
        ).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (note.id == 0L) "新建记录" else "编辑记录",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    title, { title = it }, Modifier.fillMaxWidth(),
                    label = { Text("事件标题 *") }, singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    details, { details = it }, Modifier.fillMaxWidth(),
                    label = { Text("详细内容") }, minLines = 3, maxLines = 6
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    location, { location = it }, Modifier.fillMaxWidth(),
                    label = { Text("地点 / 位置") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    singleLine = true
                )

                Spacer(Modifier.height(14.dp))
                Text("图片附件", fontWeight = FontWeight.SemiBold)
                if (imageUri.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    UriImage(
                        imageUri,
                        Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp))
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                        Icon(Icons.Default.Photo, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (imageUri.isBlank()) "添加图片" else "更换图片")
                    }
                    if (imageUri.isNotBlank()) TextButton(onClick = { imageUri = "" }) { Text("移除") }
                }

                Spacer(Modifier.height(14.dp))
                Text("事件时间", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                if (eventTime == null) {
                    Button(onClick = {
                        eventTime = System.currentTimeMillis() + 60 * 60 * 1000
                        pickDate()
                    }) {
                        Icon(Icons.Default.Event, null)
                        Spacer(Modifier.width(6.dp))
                        Text("设置时间")
                    }
                } else {
                    Text(formatTime(eventTime!!), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = ::pickDate) { Text("改日期") }
                        TextButton(onClick = ::pickTime) { Text("改时间") }
                        TextButton(onClick = {
                            eventTime = null
                            reminderEnabled = false
                        }) { Text("清除") }
                    }
                }

                if (eventTime != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("到点通知", fontWeight = FontWeight.SemiBold)
                            Text("事件时间到达后发送系统通知", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(reminderEnabled, { reminderEnabled = it })
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                note.copy(
                                    title = title.trim(),
                                    details = details.trim(),
                                    location = location.trim(),
                                    eventTime = eventTime,
                                    reminderEnabled = reminderEnabled && eventTime != null,
                                    imageUri = imageUri
                                )
                            )
                        },
                        enabled = title.isNotBlank()
                    ) { Text("保存") }
                }
            }
        }
    }
}

@Composable
private fun CourseEditorDialog(
    course: Course,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit
) {
    val context = LocalContext.current
    var name by remember(course.id) { mutableStateOf(course.name) }
    var teacher by remember(course.id) { mutableStateOf(course.teacher) }
    var location by remember(course.id) { mutableStateOf(course.location) }
    var day by remember(course.id) { mutableStateOf(course.dayOfWeek) }
    var startMinute by remember(course.id) { mutableStateOf(course.startMinute) }
    var endMinute by remember(course.id) { mutableStateOf(course.endMinute) }
    var note by remember(course.id) { mutableStateOf(course.note) }

    fun pickMinute(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            current / 60,
            current % 60,
            true
        ).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (course.id == 0L) "添加课程" else "编辑课程",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("课程名称 *") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(teacher, { teacher = it }, Modifier.fillMaxWidth(), label = { Text("老师") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("教室 / 地点") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Text("星期", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                WeekdayChips(day) { day = it }
                Spacer(Modifier.height(12.dp))
                Text("上课时间", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickMinute(startMinute) { startMinute = it } }) {
                        Text("开始 ${formatMinute(startMinute)}")
                    }
                    OutlinedButton(onClick = { pickMinute(endMinute) { endMinute = it } }) {
                        Text("结束 ${formatMinute(endMinute)}")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2, maxLines = 4)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                course.copy(
                                    name = name.trim(),
                                    teacher = teacher.trim(),
                                    location = location.trim(),
                                    dayOfWeek = day,
                                    startMinute = startMinute,
                                    endMinute = endMinute.coerceAtLeast(startMinute + 1).coerceAtMost(1439),
                                    note = note.trim()
                                )
                            )
                        },
                        enabled = name.isNotBlank()
                    ) { Text("保存") }
                }
            }
        }
    }
}

private fun colorSchemeFor(theme: ThemePreset): ColorScheme = when (theme) {
    ThemePreset.CREAM -> lightColorScheme(
        primary = Color(0xFFE76F51),
        secondary = Color(0xFFF4A261),
        tertiary = Color(0xFFE9C46A),
        background = Color(0xFFFFF8F0),
        surface = Color(0xFFFFFBF7)
    )
    ThemePreset.SAKURA -> lightColorScheme(
        primary = Color(0xFFD95F8D),
        secondary = Color(0xFFF28DB2),
        tertiary = Color(0xFFB784C4),
        background = Color(0xFFFFF5F8),
        surface = Color(0xFFFFFAFC)
    )
    ThemePreset.SKY -> lightColorScheme(
        primary = Color(0xFF3478C0),
        secondary = Color(0xFF5AA9E6),
        tertiary = Color(0xFF7FC8F8),
        background = Color(0xFFF4FAFF),
        surface = Color(0xFFFAFDFF)
    )
    ThemePreset.MINT -> lightColorScheme(
        primary = Color(0xFF2E8B75),
        secondary = Color(0xFF55BFA0),
        tertiary = Color(0xFF8FD5B6),
        background = Color(0xFFF3FBF7),
        surface = Color(0xFFFAFFFC)
    )
    ThemePreset.DARK -> darkColorScheme(
        primary = Color(0xFFFFB59A),
        secondary = Color(0xFFFFC78A),
        tertiary = Color(0xFFFFD180)
    )
}

private fun persistReadPermission(context: android.content.Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun formatTime(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))

private fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

private fun currentWeekday(): Int = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
    Calendar.MONDAY -> 1
    Calendar.TUESDAY -> 2
    Calendar.WEDNESDAY -> 3
    Calendar.THURSDAY -> 4
    Calendar.FRIDAY -> 5
    Calendar.SATURDAY -> 6
    else -> 7
}

private fun weekdayName(day: Int): String = when (day) {
    1 -> "星期一"
    2 -> "星期二"
    3 -> "星期三"
    4 -> "星期四"
    5 -> "星期五"
    6 -> "星期六"
    else -> "星期日"
}

private fun weekdayShort(day: Int): String = when (day) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    else -> "日"
}
