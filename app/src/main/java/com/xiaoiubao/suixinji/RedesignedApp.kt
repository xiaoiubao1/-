package com.xiaoiubao.suixinji

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.xiaoiubao.suixinji.data.Course
import com.xiaoiubao.suixinji.data.EventNote
import com.xiaoiubao.suixinji.reminder.NotificationTester
import com.xiaoiubao.suixinji.settings.AppSettings
import com.xiaoiubao.suixinji.settings.BackgroundStyle
import com.xiaoiubao.suixinji.settings.IconStyle
import com.xiaoiubao.suixinji.settings.LauncherIconManager
import com.xiaoiubao.suixinji.settings.ThemePreset
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private enum class MainSection { TIMETABLE, NOTES, SETTINGS }

private data class PeriodSlot(val number: Int, val startMinute: Int, val endMinute: Int)

private val periodSlots = listOf(
    PeriodSlot(1, 8 * 60 + 30, 9 * 60 + 15),
    PeriodSlot(2, 9 * 60 + 25, 10 * 60 + 10),
    PeriodSlot(3, 10 * 60 + 30, 11 * 60 + 15),
    PeriodSlot(4, 11 * 60 + 25, 12 * 60 + 10),
    PeriodSlot(5, 14 * 60 + 30, 15 * 60 + 15),
    PeriodSlot(6, 15 * 60 + 25, 16 * 60 + 10),
    PeriodSlot(7, 17 * 60 + 30, 18 * 60 + 15),
    PeriodSlot(8, 18 * 60 + 25, 19 * 60 + 10)
)

private val LocalGlassStrength = staticCompositionLocalOf { 0.60f }

@Composable
fun SuixinjiRoot(
    viewModel: MainViewModel,
    targetEventId: Long,
    targetCourseId: Long,
    settings: AppSettings
) {
    val context = LocalContext.current
    val events by viewModel.events.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val operationMessage by viewModel.importMessage.collectAsState()

    var section by remember { mutableStateOf(MainSection.TIMETABLE) }
    var editingEvent by remember { mutableStateOf<EventNote?>(null) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }

    var theme by remember { mutableStateOf(settings.theme) }
    var backgroundStyle by remember { mutableStateOf(settings.backgroundStyle) }
    var customBackgroundEnabled by remember { mutableStateOf(settings.customBackgroundEnabled) }
    var customBackgroundUri by remember { mutableStateOf(settings.wallpaper) }
    var glassStrength by remember { mutableStateOf(settings.glassStrength) }
    var iconStyle by remember { mutableStateOf(settings.iconStyle) }

    var handledEventTarget by remember(targetEventId) { mutableStateOf(false) }
    var handledCourseTarget by remember(targetCourseId) { mutableStateOf(false) }
    var pendingNotificationTest by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingNotificationTest) NotificationTester.send(context)
        pendingNotificationTest = false
    }

    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            customBackgroundUri = it.toString()
            customBackgroundEnabled = true
            settings.wallpaper = customBackgroundUri
            settings.customBackgroundEnabled = true
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

    val csvExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(viewModel::exportCsv) }

    val backupExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let(viewModel::createBackup) }

    val backupRestorer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            pendingRestoreUri = it
        }
    }

    LaunchedEffect(Unit) {
        LauncherIconManager.apply(context, iconStyle)
    }

    LaunchedEffect(events, targetEventId) {
        if (!handledEventTarget && targetEventId > 0 && events.isNotEmpty()) {
            events.firstOrNull { it.id == targetEventId }?.let {
                section = MainSection.NOTES
                editingEvent = it
                handledEventTarget = true
            }
        }
    }

    LaunchedEffect(courses, targetCourseId) {
        if (!handledCourseTarget && targetCourseId > 0 && courses.isNotEmpty()) {
            courses.firstOrNull { it.id == targetCourseId }?.let {
                section = MainSection.TIMETABLE
                editingCourse = it
                handledCourseTarget = true
            }
        }
    }

    LaunchedEffect(operationMessage) {
        if (operationMessage?.startsWith("恢复完成") == true) {
            theme = settings.theme
            backgroundStyle = settings.backgroundStyle
            customBackgroundEnabled = settings.customBackgroundEnabled
            customBackgroundUri = settings.wallpaper
            glassStrength = settings.glassStrength
            iconStyle = settings.iconStyle
            LauncherIconManager.apply(context, iconStyle)
        }
    }

    MaterialTheme(colorScheme = colorSchemeFor(theme, backgroundStyle)) {
        CompositionLocalProvider(LocalGlassStrength provides glassStrength) {
            Box(Modifier.fillMaxSize()) {
                AppBackground(
                    style = backgroundStyle,
                    customEnabled = customBackgroundEnabled,
                    customUri = customBackgroundUri,
                    glassStrength = glassStrength
                )

                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                            tonalElevation = 2.dp
                        ) {
                            NavigationBarItem(
                                selected = section == MainSection.TIMETABLE,
                                onClick = { section = MainSection.TIMETABLE },
                                icon = { Icon(Icons.Default.CalendarMonth, null) },
                                label = { Text("课表") }
                            )
                            NavigationBarItem(
                                selected = section == MainSection.NOTES,
                                onClick = { section = MainSection.NOTES },
                                icon = { Icon(Icons.Default.NoteAlt, null) },
                                label = { Text("随心记") }
                            )
                            NavigationBarItem(
                                selected = section == MainSection.SETTINGS,
                                onClick = { section = MainSection.SETTINGS },
                                icon = { Icon(Icons.Default.Person, null) },
                                label = { Text("我的") }
                            )
                        }
                    }
                ) { innerPadding ->
                    when (section) {
                        MainSection.TIMETABLE -> TimetableScreen(
                            modifier = Modifier.padding(innerPadding),
                            courses = courses,
                            onEdit = { editingCourse = it },
                            onDelete = viewModel::deleteCourse,
                            onAdd = { editingCourse = it }
                        )
                        MainSection.NOTES -> NotesScreen(
                            modifier = Modifier.padding(innerPadding),
                            events = events,
                            onEdit = { editingEvent = it },
                            onToggleCompleted = viewModel::toggleCompleted,
                            onDelete = viewModel::delete,
                            onAdd = { editingEvent = EventNote() }
                        )
                        MainSection.SETTINGS -> SettingsScreen(
                            modifier = Modifier.padding(innerPadding),
                            theme = theme,
                            backgroundStyle = backgroundStyle,
                            customBackgroundEnabled = customBackgroundEnabled,
                            hasCustomBackground = customBackgroundUri.isNotBlank(),
                            glassStrength = glassStrength,
                            iconStyle = iconStyle,
                            onIconStyleChange = {
                                iconStyle = it
                                settings.iconStyle = it
                                LauncherIconManager.apply(context, it)
                            },
                            onThemeChange = {
                                theme = it
                                settings.theme = it
                            },
                            onBackgroundStyleChange = {
                                backgroundStyle = it
                                settings.backgroundStyle = it
                            },
                            onCustomBackgroundEnabledChange = {
                                customBackgroundEnabled = it
                                settings.customBackgroundEnabled = it
                            },
                            onPickBackground = { backgroundPicker.launch(arrayOf("image/*")) },
                            onGlassStrengthChange = {
                                glassStrength = it
                                settings.glassStrength = it
                            },
                            onImport = {
                                importPicker.launch(arrayOf("text/csv", "application/json", "text/plain", "*/*"))
                            },
                            onExportCsv = { csvExporter.launch("suixinji-events-${todayStamp()}.csv") },
                            onBackup = { backupExporter.launch("suixinji-backup-${todayStamp()}.suixinji") },
                            onRestore = {
                                backupRestorer.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
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
                requestNotificationPermissionIfNeeded(
                    context,
                    updated.reminderEnabled,
                    notificationPermissionLauncher
                )
                viewModel.save(updated)
                editingEvent = null
            }
        )
    }

    editingCourse?.let { course ->
        CourseEditorDialog(
            course = course,
            onDismiss = { editingCourse = null },
            onSave = { updated ->
                requestNotificationPermissionIfNeeded(
                    context,
                    updated.reminderEnabled,
                    notificationPermissionLauncher
                )
                viewModel.saveCourse(updated)
                editingCourse = null
            }
        )
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("恢复完整备份？") },
            text = { Text("恢复会覆盖当前记录和课程。建议先创建一份完整备份。") },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("取消") }
            },
            confirmButton = {
                Button(onClick = {
                    pendingRestoreUri = null
                    viewModel.restoreBackup(uri)
                }) { Text("覆盖并恢复") }
            }
        )
    }

    operationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearImportMessage,
            confirmButton = {
                TextButton(onClick = viewModel::clearImportMessage) { Text("知道了") }
            },
            title = { Text("操作结果") },
            text = { Text(message) }
        )
    }
}

@Composable
private fun AppBackground(
    style: BackgroundStyle,
    customEnabled: Boolean,
    customUri: String,
    glassStrength: Float
) {
    val baseColor = when (style) {
        BackgroundStyle.LIGHT -> Color(0xFFF6F9FE)
        BackgroundStyle.GRAY -> Color(0xFFE9EDF2)
    }
    Box(Modifier.fillMaxSize().background(baseColor)) {
        if (customEnabled && customUri.isNotBlank()) {
            UriImage(
                uriString = customUri,
                modifier = Modifier
                    .fillMaxSize()
                    .blur((glassStrength * 18f).dp),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.10f + glassStrength * 0.18f))
            )
        }
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
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier, contentScale = contentScale)
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val strength = LocalGlassStrength.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(
                alpha = (0.70f + strength * 0.22f).coerceIn(0.70f, 0.92f)
            )
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.58f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
private fun TimetableScreen(
    modifier: Modifier,
    courses: List<Course>,
    onEdit: (Course) -> Unit,
    onDelete: (Course) -> Unit,
    onAdd: (Course) -> Unit
) {
    val now = remember { Calendar.getInstance() }
    val dateText = remember { SimpleDateFormat("yyyy/M/d", Locale.getDefault()).format(Date()) }
    val weekText = remember { "第 ${now.get(Calendar.WEEK_OF_YEAR)} 周" }
    val currentDay = currentWeekday()
    val dayDates = remember { currentWeekDates() }

    Column(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dateText, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(weekText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = { onAdd(Course(dayOfWeek = currentDay)) }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(5.dp))
                Text("添加")
            }
        }
        Spacer(Modifier.height(12.dp))

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val tableWidth = if (maxWidth < 700.dp) 700.dp else maxWidth
            val horizontal = rememberScrollState()
            val vertical = rememberScrollState()

            Box(
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontal)
                    .verticalScroll(vertical)
            ) {
                Column(Modifier.width(tableWidth)) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                                Text("节次/时间", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            (1..7).forEach { day ->
                                val selected = day == currentDay
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else Color.Transparent
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            weekdayShort(day),
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (day == 7) Color(0xFFE65C65) else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            dayDates[day - 1],
                                            fontSize = 11.sp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    GlassCard(Modifier.fillMaxWidth()) {
                        periodSlots.forEachIndexed { slotIndex, slot ->
                            PeriodRow(
                                slot = slot,
                                slotIndex = slotIndex,
                                courses = courses,
                                currentDay = currentDay,
                                onEdit = onEdit,
                                onAdd = onAdd
                            )
                            if (slotIndex != periodSlots.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PeriodRow(
    slot: PeriodSlot,
    slotIndex: Int,
    courses: List<Course>,
    currentDay: Int,
    onEdit: (Course) -> Unit,
    onAdd: (Course) -> Unit
) {
    Row(Modifier.fillMaxWidth().height(102.dp)) {
        Box(
            Modifier
                .width(72.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(slot.number.toString(), fontSize = 21.sp, fontWeight = FontWeight.Medium)
                Text(formatMinute(slot.startMinute), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatMinute(slot.endMinute), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        (1..7).forEach { day ->
            val matches = courses.filter {
                it.dayOfWeek == day && nearestPeriodIndex(it.startMinute) == slotIndex
            }
            val course = matches.firstOrNull()
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (day == currentDay) MaterialTheme.colorScheme.primary.copy(alpha = 0.035f)
                        else Color.Transparent
                    )
                    .clickable {
                        if (course != null) onEdit(course)
                        else onAdd(
                            Course(
                                dayOfWeek = day,
                                startMinute = slot.startMinute,
                                endMinute = slot.endMinute
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (course != null) {
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(2.dp),
                        shape = RoundedCornerShape(11.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(6.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                course.name,
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (course.location.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    course.location,
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                                )
                            }
                            if (matches.size > 1) {
                                Text("+${matches.size - 1}", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
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

    Column(modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("随心记", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("简单记录，快速找到", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(5.dp))
                Text("记录")
            }
        }
        Spacer(Modifier.height(12.dp))

        GlassCard {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索标题、内容或地点") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "清空") }
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter == EventFilter.ALL, { filter = EventFilter.ALL }, { Text("全部") })
                FilterChip(filter == EventFilter.UPCOMING, { filter = EventFilter.UPCOMING }, { Text("待办") })
                FilterChip(filter == EventFilter.COMPLETED, { filter = EventFilter.COMPLETED }, { Text("完成") })
            }
        }

        Spacer(Modifier.height(10.dp))
        if (visibleEvents.isEmpty()) {
            EmptyState(
                title = if (query.isNotBlank() || filter != EventFilter.ALL) "没有找到记录" else "还没有记录",
                subtitle = if (query.isNotBlank() || filter != EventFilter.ALL) "换个关键词试试" else "把今天想记住的事写下来吧",
                button = if (query.isBlank() && filter == EventFilter.ALL) "新建记录" else null,
                onClick = onAdd
            )
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visibleEvents, key = { it.id }) { note ->
                    EventCard(note, { onEdit(note) }, { onToggleCompleted(note) }, { onDelete(note) })
                }
                item { Spacer(Modifier.height(18.dp)) }
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
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        if (note.imageUri.isNotBlank()) {
            UriImage(
                note.imageUri,
                Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.height(10.dp))
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
                    Text(
                        note.details,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onToggleCompleted) { Icon(Icons.Default.Check, "切换完成") }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除") }
        }
        if (note.eventTime != null || note.location.isNotBlank() || (note.reminderEnabled && !note.completed)) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                note.eventTime?.let {
                    AssistChip(onClick = onEdit, label = { Text(formatTime(it)) }, leadingIcon = { Icon(Icons.Default.Event, null, Modifier.size(17.dp)) })
                }
                if (note.location.isNotBlank()) {
                    AssistChip(onClick = onEdit, label = { Text(note.location, maxLines = 1) }, leadingIcon = { Icon(Icons.Default.LocationOn, null, Modifier.size(17.dp)) })
                }
                if (note.reminderEnabled && !note.completed) {
                    AssistChip(onClick = onEdit, label = { Text("提醒已开启") }, leadingIcon = { Icon(Icons.Default.Notifications, null, Modifier.size(17.dp)) })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    modifier: Modifier,
    theme: ThemePreset,
    backgroundStyle: BackgroundStyle,
    customBackgroundEnabled: Boolean,
    hasCustomBackground: Boolean,
    glassStrength: Float,
    iconStyle: IconStyle,
    onIconStyleChange: (IconStyle) -> Unit,
    onThemeChange: (ThemePreset) -> Unit,
    onBackgroundStyleChange: (BackgroundStyle) -> Unit,
    onCustomBackgroundEnabledChange: (Boolean) -> Unit,
    onPickBackground: () -> Unit,
    onGlassStrengthChange: (Float) -> Unit,
    onImport: () -> Unit,
    onExportCsv: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onTestNotification: () -> Unit
) {
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(14.dp))
            Text("个性化设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("外观、背景、数据与提醒", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            GlassCard {
                SettingsHeader(Icons.Default.Apps, "自定义 APP 图标", "选择你喜欢的启动图标样式")
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconStyle.entries.forEach { style ->
                        FilterChip(
                            selected = style == iconStyle,
                            onClick = { onIconStyleChange(style) },
                            label = { Text(style.title) },
                            leadingIcon = { Icon(iconForStyle(style), null, Modifier.size(18.dp)) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Android 启动器会在几秒内刷新图标；不同桌面刷新速度可能不同。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            GlassCard {
                SettingsHeader(Icons.Default.Wallpaper, "背景设置", "默认仅提供浅色和灰色，可自行选择图片")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("启用自定义背景", fontWeight = FontWeight.SemiBold)
                        Text("关闭时使用下方默认背景", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(customBackgroundEnabled, onCustomBackgroundEnabledChange)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onPickBackground,
                    enabled = customBackgroundEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Photo, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (hasCustomBackground) "更换背景图片" else "选择背景图片")
                }
                Spacer(Modifier.height(10.dp))
                Text("默认背景样式", fontWeight = FontWeight.SemiBold)
                Text("未启用自定义背景时生效", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackgroundStyle.entries.forEach { style ->
                        FilterChip(
                            selected = backgroundStyle == style,
                            onClick = { onBackgroundStyleChange(style) },
                            label = { Text(style.title) }
                        )
                    }
                }
            }
        }

        item {
            GlassCard {
                SettingsHeader(Icons.Default.BlurOn, "毛玻璃效果", "调整背景模糊与磨砂强度")
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("弱", fontSize = 12.sp)
                    Slider(
                        value = glassStrength,
                        onValueChange = onGlassStrengthChange,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text("${(glassStrength * 100).toInt()}%", fontSize = 12.sp)
                }
            }
        }

        item {
            GlassCard {
                SettingsHeader(Icons.Default.Palette, "主题颜色", "选择界面的强调色")
                Spacer(Modifier.height(8.dp))
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
            GlassCard {
                SettingsHeader(Icons.Default.NotificationsActive, "通知测试", "确认通知权限和通知渠道")
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onTestNotification) {
                    Icon(Icons.Default.NotificationsActive, null)
                    Spacer(Modifier.width(6.dp))
                    Text("发送测试通知")
                }
            }
        }

        item {
            GlassCard {
                SettingsHeader(Icons.Default.CloudSync, "导入 / 导出数据", "导入记录、导出 CSV 或完整备份")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImport) { Text("导入文件") }
                    OutlinedButton(onClick = onExportCsv) { Text("导出 CSV") }
                    Button(onClick = onBackup) { Text("完整备份") }
                    OutlinedButton(onClick = onRestore) { Text("恢复备份") }
                }
            }
        }

        item {
            GlassCard {
                SettingsHeader(Icons.Default.CalendarMonth, "课程提醒", "每门课程可在编辑页单独开启")
                Text("支持上课时、提前 5 / 10 / 15 / 30 / 60 分钟提醒。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            GlassCard {
                SettingsHeader(Icons.Default.Widgets, "桌面小组件", "显示下一条待办和今天下一节课程")
                Text("长按桌面 → 小组件 → 随心记 → 随心记概览。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SettingsHeader(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(13.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, button: String?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 46.dp),
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
                Text(if (note.id == 0L) "新建记录" else "编辑记录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("事件标题 *") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth(), label = { Text("详细内容") }, minLines = 3, maxLines = 6)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("地点 / 位置") }, singleLine = true)

                Spacer(Modifier.height(12.dp))
                Text("图片附件", fontWeight = FontWeight.SemiBold)
                if (imageUri.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    UriImage(imageUri, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                        Icon(Icons.Default.Photo, null)
                        Spacer(Modifier.width(5.dp))
                        Text(if (imageUri.isBlank()) "添加图片" else "更换图片")
                    }
                    if (imageUri.isNotBlank()) TextButton(onClick = { imageUri = "" }) { Text("移除") }
                }

                Spacer(Modifier.height(12.dp))
                Text("事件时间", fontWeight = FontWeight.SemiBold)
                if (eventTime == null) {
                    Button(onClick = {
                        eventTime = System.currentTimeMillis() + 60 * 60 * 1000
                        pickDate()
                    }) { Text("设置时间") }
                } else {
                    Text(formatTime(eventTime!!), style = MaterialTheme.typography.titleMedium)
                    Row {
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
                            Text("事件时间到达后发送通知", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(reminderEnabled, { reminderEnabled = it })
                    }
                }

                Spacer(Modifier.height(16.dp))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseEditorDialog(
    course: Course,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit
) {
    val context = LocalContext.current
    var name by remember(course.id, course.dayOfWeek, course.startMinute) { mutableStateOf(course.name) }
    var teacher by remember(course.id, course.dayOfWeek, course.startMinute) { mutableStateOf(course.teacher) }
    var location by remember(course.id, course.dayOfWeek, course.startMinute) { mutableStateOf(course.location) }
    var day by remember(course.id, course.dayOfWeek, course.startMinute) { mutableStateOf(course.dayOfWeek) }
    var startMinute by remember(course.id, course.dayOfWeek, course.startMinute) { mutableStateOf(course.startMinute) }
    var endMinute by remember(course.id, course.dayOfWeek, course.startMinute) { mutableStateOf(course.endMinute) }
    var note by remember(course.id, course.dayOfWeek, course.startMinute) { mutableStateOf(course.note) }
    var reminderEnabled by remember(course.id, course.dayOfWeek, course.startMinute) { mutableStateOf(course.reminderEnabled) }
    var reminderMinutesBefore by remember(course.id, course.dayOfWeek, course.startMinute) { mutableStateOf(course.reminderMinutesBefore) }

    fun pickMinute(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, hour, minute -> onPicked(hour * 60 + minute) }, current / 60, current % 60, true).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(if (course.id == 0L) "添加课程" else "编辑课程", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("课程名称 *") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(teacher, { teacher = it }, Modifier.fillMaxWidth(), label = { Text("老师") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("教室 / 地点") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                Text("星期", fontWeight = FontWeight.SemiBold)
                WeekdayChips(day) { day = it }
                Spacer(Modifier.height(10.dp))
                Text("上课时间", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickMinute(startMinute) { startMinute = it } }) { Text("开始 ${formatMinute(startMinute)}") }
                    OutlinedButton(onClick = { pickMinute(endMinute) { endMinute = it } }) { Text("结束 ${formatMinute(endMinute)}") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("备注") }, minLines = 2, maxLines = 4)

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("课程开始提醒", fontWeight = FontWeight.SemiBold)
                        Text("每周自动安排通知", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(reminderEnabled, { reminderEnabled = it })
                }
                if (reminderEnabled) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 5, 10, 15, 30, 60).forEach { minutes ->
                            FilterChip(
                                selected = reminderMinutesBefore == minutes,
                                onClick = { reminderMinutesBefore = minutes },
                                label = { Text(if (minutes == 0) "上课时" else "提前${minutes}分钟") }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
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
                                    note = note.trim(),
                                    reminderEnabled = reminderEnabled,
                                    reminderMinutesBefore = reminderMinutesBefore
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayChips(selectedDay: Int, onSelected: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { day ->
            FilterChip(selected = selectedDay == day, onClick = { onSelected(day) }, label = { Text(weekdayShort(day)) })
        }
    }
}

private fun iconForStyle(style: IconStyle): ImageVector = when (style) {
    IconStyle.CALENDAR -> Icons.Default.CalendarMonth
    IconStyle.CHECK -> Icons.Default.DoneAll
    IconStyle.GRID -> Icons.Default.GridView
    IconStyle.NOTE -> Icons.Default.NoteAlt
}

private fun colorSchemeFor(theme: ThemePreset, backgroundStyle: BackgroundStyle): ColorScheme {
    val background = if (backgroundStyle == BackgroundStyle.LIGHT) Color(0xFFF6F9FE) else Color(0xFFE9EDF2)
    val surface = if (backgroundStyle == BackgroundStyle.LIGHT) Color(0xFFFBFCFF) else Color(0xFFF3F5F7)
    return when (theme) {
        ThemePreset.CREAM -> lightColorScheme(primary = Color(0xFF3267E3), secondary = Color(0xFF5B8DF1), background = background, surface = surface)
        ThemePreset.SAKURA -> lightColorScheme(primary = Color(0xFF7657D8), secondary = Color(0xFF987AE8), background = background, surface = surface)
        ThemePreset.SKY -> lightColorScheme(primary = Color(0xFF1F9CA5), secondary = Color(0xFF36BCC4), background = background, surface = surface)
        ThemePreset.MINT -> lightColorScheme(primary = Color(0xFFD94F7A), secondary = Color(0xFFF2799C), background = background, surface = surface)
        ThemePreset.DARK -> lightColorScheme(primary = Color(0xFFE58A2B), secondary = Color(0xFFF3A94D), background = background, surface = surface)
    }
}

private fun nearestPeriodIndex(minute: Int): Int = periodSlots.indices.minByOrNull {
    abs(periodSlots[it].startMinute - minute)
} ?: 0

private fun currentWeekDates(): List<String> {
    val calendar = Calendar.getInstance()
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    val offsetToMonday = when (dayOfWeek) {
        Calendar.SUNDAY -> -6
        else -> Calendar.MONDAY - dayOfWeek
    }
    calendar.add(Calendar.DAY_OF_MONTH, offsetToMonday)
    return (1..7).map {
        val value = "${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.DAY_OF_MONTH)}"
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        value
    }
}

private fun requestNotificationPermissionIfNeeded(
    context: android.content.Context,
    enabled: Boolean,
    launcher: androidx.activity.result.ActivityResultLauncher<String>
) {
    if (
        enabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private fun persistReadPermission(context: android.content.Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun formatTime(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))

private fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

private fun todayStamp(): String = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

private fun currentWeekday(): Int = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
    Calendar.MONDAY -> 1
    Calendar.TUESDAY -> 2
    Calendar.WEDNESDAY -> 3
    Calendar.THURSDAY -> 4
    Calendar.FRIDAY -> 5
    Calendar.SATURDAY -> 6
    else -> 7
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
