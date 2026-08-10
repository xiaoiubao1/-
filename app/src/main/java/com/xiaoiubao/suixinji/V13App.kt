package com.xiaoiubao.suixinji

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoiubao.suixinji.data.Course
import com.xiaoiubao.suixinji.data.EventNote
import com.xiaoiubao.suixinji.reminder.NotificationTester
import com.xiaoiubao.suixinji.settings.AppSettings
import com.xiaoiubao.suixinji.settings.BackgroundStyle
import com.xiaoiubao.suixinji.settings.ThemePreset
import com.xiaoiubao.suixinji.settings.WidgetTextMode
import com.xiaoiubao.suixinji.widget.EventWidgetProvider
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private enum class V13Section { TIMETABLE, NOTES, SETTINGS }
private data class V13Period(val number: Int, val start: Int, val end: Int)

private val v13Periods = listOf(
    V13Period(1, 8 * 60 + 30, 9 * 60 + 15),
    V13Period(2, 9 * 60 + 25, 10 * 60 + 10),
    V13Period(3, 10 * 60 + 30, 11 * 60 + 15),
    V13Period(4, 11 * 60 + 25, 12 * 60 + 10),
    V13Period(5, 14 * 60 + 30, 15 * 60 + 15),
    V13Period(6, 15 * 60 + 25, 16 * 60 + 10),
    V13Period(7, 17 * 60 + 30, 18 * 60 + 15),
    V13Period(8, 18 * 60 + 25, 19 * 60 + 10)
)

@Composable
fun SuixinjiRootV13(
    viewModel: MainViewModel,
    targetEventId: Long,
    targetCourseId: Long,
    settings: AppSettings
) {
    val context = LocalContext.current
    val events by viewModel.events.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val operationMessage by viewModel.importMessage.collectAsState()

    var section by remember { mutableStateOf(V13Section.TIMETABLE) }
    var editingEvent by remember { mutableStateOf<EventNote?>(null) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }

    var theme by remember { mutableStateOf(settings.theme) }
    var backgroundStyle by remember { mutableStateOf(settings.backgroundStyle) }
    var customBackgroundEnabled by remember { mutableStateOf(settings.customBackgroundEnabled) }
    var backgroundUri by remember { mutableStateOf(settings.wallpaper) }
    var glassStrength by remember { mutableFloatStateOf(settings.glassStrength) }

    var widgetBackgroundUri by remember { mutableStateOf(settings.widgetBackgroundUri) }
    var widgetBackgroundColor by remember { mutableIntStateOf(settings.widgetBackgroundColor) }
    var widgetTextMode by remember { mutableStateOf(settings.widgetTextMode) }
    var widgetAccentColor by remember { mutableIntStateOf(settings.widgetAccentColor) }
    var widgetOpacity by remember { mutableFloatStateOf(settings.widgetOpacity) }
    var widgetFrosted by remember { mutableStateOf(settings.widgetFrosted) }

    var pendingNotificationTest by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var handledEventTarget by remember(targetEventId) { mutableStateOf(false) }
    var handledCourseTarget by remember(targetCourseId) { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingNotificationTest) NotificationTester.send(context)
        pendingNotificationTest = false
    }

    val appBackgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistUriPermission(context, it)
            backgroundUri = it.toString()
            customBackgroundEnabled = true
            settings.wallpaper = backgroundUri
            settings.customBackgroundEnabled = true
        }
    }

    val widgetBackgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistUriPermission(context, it)
            widgetBackgroundUri = it.toString()
            settings.widgetBackgroundUri = widgetBackgroundUri
            EventWidgetProvider.updateAll(context)
        }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistUriPermission(context, it)
            viewModel.importFromUri(it)
        }
    }
    val csvExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let(viewModel::createBackup)
    }
    val backupRestorer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistUriPermission(context, it)
            pendingRestoreUri = it
        }
    }

    LaunchedEffect(events, targetEventId) {
        if (!handledEventTarget && targetEventId > 0 && events.isNotEmpty()) {
            events.firstOrNull { it.id == targetEventId }?.let {
                section = V13Section.NOTES
                editingEvent = it
                handledEventTarget = true
            }
        }
    }
    LaunchedEffect(courses, targetCourseId) {
        if (!handledCourseTarget && targetCourseId > 0 && courses.isNotEmpty()) {
            courses.firstOrNull { it.id == targetCourseId }?.let {
                section = V13Section.TIMETABLE
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
            backgroundUri = settings.wallpaper
            glassStrength = settings.glassStrength
            widgetBackgroundUri = settings.widgetBackgroundUri
            widgetBackgroundColor = settings.widgetBackgroundColor
            widgetTextMode = settings.widgetTextMode
            widgetAccentColor = settings.widgetAccentColor
            widgetOpacity = settings.widgetOpacity
            widgetFrosted = settings.widgetFrosted
            EventWidgetProvider.updateAll(context)
        }
    }

    MaterialTheme(colorScheme = v13ColorScheme(theme, backgroundStyle)) {
        Box(Modifier.fillMaxSize()) {
            V13Background(backgroundStyle, customBackgroundEnabled, backgroundUri, glassStrength)
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.91f)) {
                        NavigationBarItem(
                            selected = section == V13Section.TIMETABLE,
                            onClick = { section = V13Section.TIMETABLE },
                            icon = { Icon(Icons.Default.CalendarMonth, null) },
                            label = { Text("课表") }
                        )
                        NavigationBarItem(
                            selected = section == V13Section.NOTES,
                            onClick = { section = V13Section.NOTES },
                            icon = { Icon(Icons.Default.NoteAlt, null) },
                            label = { Text("随心记") }
                        )
                        NavigationBarItem(
                            selected = section == V13Section.SETTINGS,
                            onClick = { section = V13Section.SETTINGS },
                            icon = { Icon(Icons.Default.Person, null) },
                            label = { Text("我的") }
                        )
                    }
                }
            ) { padding ->
                when (section) {
                    V13Section.TIMETABLE -> V13Timetable(
                        Modifier.padding(padding), courses,
                        onEdit = { editingCourse = it },
                        onAdd = { editingCourse = it }
                    )
                    V13Section.NOTES -> V13Notes(
                        Modifier.padding(padding), events,
                        onEdit = { editingEvent = it },
                        onAdd = { editingEvent = EventNote() },
                        onToggle = viewModel::toggleCompleted,
                        onDelete = viewModel::delete
                    )
                    V13Section.SETTINGS -> V13Settings(
                        modifier = Modifier.padding(padding),
                        theme = theme,
                        backgroundStyle = backgroundStyle,
                        customBackgroundEnabled = customBackgroundEnabled,
                        hasBackground = backgroundUri.isNotBlank(),
                        glassStrength = glassStrength,
                        widgetBackgroundUri = widgetBackgroundUri,
                        widgetBackgroundColor = widgetBackgroundColor,
                        widgetTextMode = widgetTextMode,
                        widgetAccentColor = widgetAccentColor,
                        widgetOpacity = widgetOpacity,
                        widgetFrosted = widgetFrosted,
                        onTheme = { theme = it; settings.theme = it },
                        onBackgroundStyle = { backgroundStyle = it; settings.backgroundStyle = it },
                        onCustomBackground = { customBackgroundEnabled = it; settings.customBackgroundEnabled = it },
                        onPickBackground = { appBackgroundPicker.launch(arrayOf("image/*")) },
                        onGlassStrength = { glassStrength = it; settings.glassStrength = it },
                        onPickWidgetBackground = { widgetBackgroundPicker.launch(arrayOf("image/*")) },
                        onClearWidgetBackground = {
                            widgetBackgroundUri = ""
                            settings.widgetBackgroundUri = ""
                            EventWidgetProvider.updateAll(context)
                        },
                        onWidgetBackgroundColor = {
                            widgetBackgroundColor = it
                            settings.widgetBackgroundColor = it
                            EventWidgetProvider.updateAll(context)
                        },
                        onWidgetTextMode = {
                            widgetTextMode = it
                            settings.widgetTextMode = it
                            EventWidgetProvider.updateAll(context)
                        },
                        onWidgetAccentColor = {
                            widgetAccentColor = it
                            settings.widgetAccentColor = it
                            EventWidgetProvider.updateAll(context)
                        },
                        onWidgetOpacity = {
                            widgetOpacity = it
                            settings.widgetOpacity = it
                            EventWidgetProvider.updateAll(context)
                        },
                        onWidgetFrosted = {
                            widgetFrosted = it
                            settings.widgetFrosted = it
                            EventWidgetProvider.updateAll(context)
                        },
                        onNotificationTest = {
                            if (NotificationTester.canNotify(context)) NotificationTester.send(context)
                            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                pendingNotificationTest = true
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onImport = { importPicker.launch(arrayOf("text/csv", "application/json", "text/plain", "*/*")) },
                        onCsv = { csvExporter.launch("suixinji-events-${v13TodayStamp()}.csv") },
                        onBackup = { backupExporter.launch("suixinji-backup-${v13TodayStamp()}.suixinji") },
                        onRestore = { backupRestorer.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                    )
                }
            }
        }
    }

    editingEvent?.let { note ->
        V13EventEditor(note, { editingEvent = null }) { updated ->
            requestNotifyPermission(context, updated.reminderEnabled, notificationPermissionLauncher)
            viewModel.save(updated)
            editingEvent = null
        }
    }
    editingCourse?.let { course ->
        V13CourseEditor(course, { editingCourse = null }, { viewModel.deleteCourse(course); editingCourse = null }) { updated ->
            requestNotifyPermission(context, updated.reminderEnabled, notificationPermissionLauncher)
            viewModel.saveCourse(updated)
            editingCourse = null
        }
    }
    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("恢复完整备份？") },
            text = { Text("恢复会覆盖当前记录和课程。建议先导出一份当前备份。") },
            dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text("取消") } },
            confirmButton = {
                Button(onClick = { pendingRestoreUri = null; viewModel.restoreBackup(uri) }) { Text("覆盖并恢复") }
            }
        )
    }
    operationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearImportMessage,
            title = { Text("操作结果") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearImportMessage) { Text("知道了") } }
        )
    }
}

@Composable
private fun V13Background(style: BackgroundStyle, custom: Boolean, uri: String, strength: Float) {
    val base = if (style == BackgroundStyle.LIGHT) Color(0xFFF7F8FC) else Color(0xFFE7E9ED)
    Box(Modifier.fillMaxSize().background(base)) {
        if (custom && uri.isNotBlank()) {
            V13UriImage(uri, Modifier.fillMaxSize().blur((strength * 16f).dp))
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f + strength * 0.18f)))
        }
    }
}

@Composable
private fun V13UriImage(uri: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()
    }
    bitmap?.let { Image(it, null, modifier, contentScale = contentScale) }
}

@Composable
private fun V13GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.52f)),
        elevation = CardDefaults.cardElevation(1.dp)
    ) { Column(Modifier.fillMaxWidth().padding(12.dp), content = content) }
}

@Composable
private fun V13Timetable(
    modifier: Modifier,
    courses: List<Course>,
    onEdit: (Course) -> Unit,
    onAdd: (Course) -> Unit
) {
    val currentDay = v13CurrentWeekday()
    val dates = remember { v13CurrentWeekDates() }
    val now = remember { Calendar.getInstance() }
    val dateText = remember { SimpleDateFormat("yyyy/M/d", Locale.getDefault()).format(Date()) }

    Column(modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dateText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("第 ${now.get(Calendar.WEEK_OF_YEAR)} 周", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(
                onClick = { onAdd(Course(dayOfWeek = currentDay)) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(3.dp)); Text("添加") }
        }
        Spacer(Modifier.height(6.dp))

        V13GlassCard(Modifier.weight(1f).fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val headerHeight = 38.dp
                val rowHeight = ((maxHeight - headerHeight - 4.dp) / 8f).coerceIn(38.dp, 62.dp)
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().height(headerHeight), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(46.dp), contentAlignment = Alignment.Center) {
                            Text("节次", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        (1..7).forEach { day ->
                            val selected = day == currentDay
                            Box(
                                Modifier.weight(1f).fillMaxHeight().padding(horizontal = 1.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(v13Weekday(day), fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                    Text(dates[day - 1], fontSize = 8.sp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    v13Periods.forEachIndexed { index, period ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        Row(Modifier.fillMaxWidth().height(rowHeight)) {
                            Column(
                                Modifier.width(46.dp).fillMaxHeight(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(period.number.toString(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(v13Minute(period.start), fontSize = 7.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            (1..7).forEach { day ->
                                val matches = courses.filter { it.dayOfWeek == day && v13NearestPeriod(it.startMinute) == index }
                                val course = matches.firstOrNull()
                                Box(
                                    Modifier.weight(1f).fillMaxHeight().padding(1.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(if (day == currentDay) MaterialTheme.colorScheme.primary.copy(alpha = 0.025f) else Color.Transparent)
                                        .clickable {
                                            if (course != null) onEdit(course)
                                            else onAdd(Course(dayOfWeek = day, startMinute = period.start, endMinute = period.end))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (course != null) {
                                        Surface(
                                            Modifier.fillMaxSize(),
                                            shape = RoundedCornerShape(7.dp),
                                            color = v13CourseColor(course.name).copy(alpha = 0.78f)
                                        ) {
                                            Column(
                                                Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 2.dp),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(course.name, fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                if (course.location.isNotBlank() && rowHeight >= 46.dp) {
                                                    Text(course.location, fontSize = 6.5.sp, lineHeight = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                                                }
                                                if (matches.size > 1) Text("+${matches.size - 1}", fontSize = 6.sp, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V13Notes(
    modifier: Modifier,
    events: List<EventNote>,
    onEdit: (EventNote) -> Unit,
    onAdd: () -> Unit,
    onToggle: (EventNote) -> Unit,
    onDelete: (EventNote) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(EventFilter.ALL) }
    val visible = remember(events, query, filter) {
        val q = query.trim().lowercase()
        events.filter {
            val match = q.isBlank() || it.title.lowercase().contains(q) || it.details.lowercase().contains(q) || it.location.lowercase().contains(q)
            val status = when (filter) {
                EventFilter.ALL -> true
                EventFilter.UPCOMING -> !it.completed
                EventFilter.COMPLETED -> it.completed
            }
            match && status
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("随心记", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("简单记录，快速找到", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onAdd) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("记录") }
        }
        Spacer(Modifier.height(8.dp))
        V13GlassCard {
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("搜索标题、内容或地点") }, leadingIcon = { Icon(Icons.Default.Search, null) }
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(filter == EventFilter.ALL, { filter = EventFilter.ALL }, { Text("全部") })
                FilterChip(filter == EventFilter.UPCOMING, { filter = EventFilter.UPCOMING }, { Text("待办") })
                FilterChip(filter == EventFilter.COMPLETED, { filter = EventFilter.COMPLETED }, { Text("完成") })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无记录") }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.id }) { note ->
                    V13GlassCard(Modifier.fillMaxWidth().clickable { onEdit(note) }) {
                        if (note.imageUri.isNotBlank()) {
                            V13UriImage(note.imageUri, Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)))
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(note.title.ifBlank { "未命名记录" }, fontWeight = FontWeight.Bold, textDecoration = if (note.completed) TextDecoration.LineThrough else null)
                                if (note.details.isNotBlank()) Text(note.details, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                note.eventTime?.let { Text(v13FormatTime(it), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary) }
                            }
                            IconButton(onClick = { onToggle(note) }) { Icon(Icons.Default.Check, "完成") }
                            IconButton(onClick = { onDelete(note) }) { Icon(Icons.Default.Delete, "删除") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V13Settings(
    modifier: Modifier,
    theme: ThemePreset,
    backgroundStyle: BackgroundStyle,
    customBackgroundEnabled: Boolean,
    hasBackground: Boolean,
    glassStrength: Float,
    widgetBackgroundUri: String,
    widgetBackgroundColor: Int,
    widgetTextMode: WidgetTextMode,
    widgetAccentColor: Int,
    widgetOpacity: Float,
    widgetFrosted: Boolean,
    onTheme: (ThemePreset) -> Unit,
    onBackgroundStyle: (BackgroundStyle) -> Unit,
    onCustomBackground: (Boolean) -> Unit,
    onPickBackground: () -> Unit,
    onGlassStrength: (Float) -> Unit,
    onPickWidgetBackground: () -> Unit,
    onClearWidgetBackground: () -> Unit,
    onWidgetBackgroundColor: (Int) -> Unit,
    onWidgetTextMode: (WidgetTextMode) -> Unit,
    onWidgetAccentColor: (Int) -> Unit,
    onWidgetOpacity: (Float) -> Unit,
    onWidgetFrosted: (Boolean) -> Unit,
    onNotificationTest: () -> Unit,
    onImport: () -> Unit,
    onCsv: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    val backgroundColors = listOf(0xFFF4F1FA.toInt(), 0xFFF9E7EF.toInt(), 0xFFE6F1FA.toInt(), 0xFFE9F4EC.toInt(), 0xFF20242A.toInt(), 0xFFF3EEE6.toInt())
    val accentColors = listOf(0xFF7B61D1.toInt(), 0xFFE45B83.toInt(), 0xFF4A9EE8.toInt(), 0xFF4EB67B.toInt(), 0xFFF29C38.toInt())

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("我的", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("外观、桌面小组件、数据与提醒", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            V13GlassCard {
                V13SettingTitle(Icons.Default.Wallpaper, "应用背景", "原版仅浅色 / 灰色，自定义图片由你决定")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用自定义背景", Modifier.weight(1f))
                    Switch(customBackgroundEnabled, onCustomBackground)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BackgroundStyle.entries.forEach { style ->
                        FilterChip(style == backgroundStyle, { onBackgroundStyle(style) }, { Text(style.title) })
                    }
                }
                OutlinedButton(onClick = onPickBackground, enabled = customBackgroundEnabled, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Photo, null); Spacer(Modifier.width(4.dp)); Text(if (hasBackground) "更换背景图片" else "选择背景图片")
                }
                Text("毛玻璃强度 ${(glassStrength * 100).toInt()}%", fontSize = 12.sp)
                Slider(glassStrength, onGlassStrength)
            }
        }
        item {
            V13GlassCard {
                V13SettingTitle(Icons.Default.Widgets, "桌面小组件 DIY", "背景图片、颜色、文字、透明度与磨砂都可调整")
                Spacer(Modifier.height(8.dp))
                V13WidgetPreview(widgetBackgroundUri, widgetBackgroundColor, widgetTextMode, widgetAccentColor, widgetOpacity, widgetFrosted)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = onPickWidgetBackground) { Text(if (widgetBackgroundUri.isBlank()) "选择背景图" else "更换背景图") }
                    if (widgetBackgroundUri.isNotBlank()) OutlinedButton(onClick = onClearWidgetBackground) { Text("移除图片") }
                }
                Spacer(Modifier.height(8.dp))
                Text("背景颜色", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    backgroundColors.forEach { value -> V13ColorDot(value, value == widgetBackgroundColor) { onWidgetBackgroundColor(value) } }
                }
                Spacer(Modifier.height(8.dp))
                Text("文字颜色", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WidgetTextMode.entries.forEach { mode -> FilterChip(mode == widgetTextMode, { onWidgetTextMode(mode) }, { Text(mode.title) }) }
                }
                Spacer(Modifier.height(8.dp))
                Text("强调色", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    accentColors.forEach { value -> V13ColorDot(value, value == widgetAccentColor) { onWidgetAccentColor(value) } }
                }
                Spacer(Modifier.height(8.dp))
                Text("背景透明度 ${(widgetOpacity * 100).toInt()}%", fontSize = 12.sp)
                Slider(widgetOpacity, onWidgetOpacity, valueRange = 0.35f..1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("磨砂覆盖", fontWeight = FontWeight.SemiBold)
                        Text("RemoteViews 兼容方案：半透明叠层模拟毛玻璃", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(widgetFrosted, onWidgetFrosted)
                }
                Text("修改后会立即刷新桌面上已经添加的随心记小组件。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            V13GlassCard {
                V13SettingTitle(Icons.Default.Palette, "主题颜色", "选择界面强调色")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemePreset.entries.forEach { preset -> FilterChip(preset == theme, { onTheme(preset) }, { Text(preset.title) }) }
                }
            }
        }
        item {
            V13GlassCard {
                V13SettingTitle(Icons.Default.NotificationsActive, "通知测试", "确认通知权限与渠道可用")
                FilledTonalButton(onClick = onNotificationTest) { Text("发送测试通知") }
            }
        }
        item {
            V13GlassCard {
                V13SettingTitle(Icons.Default.CloudSync, "数据", "导入、CSV 导出、完整备份与恢复")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onImport) { Text("导入") }
                    OutlinedButton(onClick = onCsv) { Text("CSV") }
                    Button(onClick = onBackup) { Text("备份") }
                    OutlinedButton(onClick = onRestore) { Text("恢复") }
                }
            }
        }
        item {
            V13GlassCard {
                V13SettingTitle(Icons.Default.CalendarMonth, "课程提醒", "编辑每门课程时可独立设置")
                Text("支持上课时或提前 5 / 10 / 15 / 30 / 60 分钟提醒。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun V13WidgetPreview(uri: String, backgroundColor: Int, textMode: WidgetTextMode, accentColor: Int, opacity: Float, frosted: Boolean) {
    val text = v13WidgetTextColor(textMode, backgroundColor, uri.isNotBlank())
    Box(
        Modifier.fillMaxWidth().height(142.dp).clip(RoundedCornerShape(24.dp)).background(Color(backgroundColor).copy(alpha = opacity))
    ) {
        if (uri.isNotBlank()) V13UriImage(uri, Modifier.fillMaxSize())
        if (frosted) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.18f)))
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
            Text("随心记", color = Color(accentColor), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("下一条待办", color = text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("今天 18:30 · 图书馆", color = text.copy(alpha = 0.78f), fontSize = 12.sp)
            Spacer(Modifier.height(7.dp))
            Text("14:30 程序设计 · 机房1", color = Color(accentColor), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun V13ColorDot(value: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(if (selected) 34.dp else 30.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(50), color = Color(value),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {}
}

@Composable
private fun V13SettingTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(38.dp), RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun V13EventEditor(note: EventNote, onDismiss: () -> Unit, onSave: (EventNote) -> Unit) {
    val context = LocalContext.current
    var title by remember(note.id) { mutableStateOf(note.title) }
    var details by remember(note.id) { mutableStateOf(note.details) }
    var location by remember(note.id) { mutableStateOf(note.location) }
    var eventTime by remember(note.id) { mutableStateOf(note.eventTime) }
    var reminder by remember(note.id) { mutableStateOf(note.reminderEnabled) }
    var imageUri by remember(note.id) { mutableStateOf(note.imageUri) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persistUriPermission(context, it); imageUri = it.toString() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (note.id == 0L) "新建随心记" else "编辑随心记") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("标题") })
                OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth(), label = { Text("内容") }, minLines = 3)
                OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("地点") })
                OutlinedButton(onClick = { v13PickDateTime(context, eventTime) { eventTime = it } }, modifier = Modifier.fillMaxWidth()) {
                    Text(eventTime?.let(::v13FormatTime) ?: "选择日期和时间")
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("到点提醒", Modifier.weight(1f)); Switch(reminder, { reminder = it }) }
                OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text(if (imageUri.isBlank()) "添加图片" else "更换图片") }
                if (imageUri.isNotBlank()) V13UriImage(imageUri, Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            Button(onClick = {
                if (title.isNotBlank()) onSave(note.copy(title = title.trim(), details = details.trim(), location = location.trim(), eventTime = eventTime, reminderEnabled = reminder, imageUri = imageUri))
            }, enabled = title.isNotBlank()) { Text("保存") }
        }
    )
}

@Composable
private fun V13CourseEditor(course: Course, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Course) -> Unit) {
    val context = LocalContext.current
    var name by remember(course.id) { mutableStateOf(course.name) }
    var teacher by remember(course.id) { mutableStateOf(course.teacher) }
    var location by remember(course.id) { mutableStateOf(course.location) }
    var day by remember(course.id) { mutableIntStateOf(course.dayOfWeek.coerceIn(1, 7)) }
    var start by remember(course.id) { mutableIntStateOf(course.startMinute) }
    var end by remember(course.id) { mutableIntStateOf(course.endMinute) }
    var note by remember(course.id) { mutableStateOf(course.note) }
    var reminder by remember(course.id) { mutableStateOf(course.reminderEnabled) }
    var before by remember(course.id) { mutableIntStateOf(course.reminderMinutesBefore) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (course.id == 0L) "添加课程" else "编辑课程") },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("课程名称") })
                OutlinedTextField(teacher, { teacher = it }, Modifier.fillMaxWidth(), label = { Text("老师") })
                OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("教室") })
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { d -> FilterChip(d == day, { day = d }, { Text(v13Weekday(d), fontSize = 10.sp) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { v13PickMinute(context, start) { start = it } }, Modifier.weight(1f)) { Text("开始 ${v13Minute(start)}") }
                    OutlinedButton(onClick = { v13PickMinute(context, end) { end = it } }, Modifier.weight(1f)) { Text("结束 ${v13Minute(end)}") }
                }
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("备注") })
                Row(verticalAlignment = Alignment.CenterVertically) { Text("课程开始提醒", Modifier.weight(1f)); Switch(reminder, { reminder = it }) }
                if (reminder) {
                    Text("提前提醒", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0, 5, 10, 15, 30, 60).forEach { m -> FilterChip(m == before, { before = m }, { Text(if (m == 0) "上课时" else "$m 分", fontSize = 9.sp) }) }
                    }
                }
                if (course.id != 0L) OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("删除课程") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) onSave(course.copy(name = name.trim(), teacher = teacher.trim(), location = location.trim(), dayOfWeek = day, startMinute = start, endMinute = end, note = note.trim(), reminderEnabled = reminder, reminderMinutesBefore = before))
            }, enabled = name.isNotBlank()) { Text("保存") }
        }
    )
}

private fun v13ColorScheme(theme: ThemePreset, backgroundStyle: BackgroundStyle): ColorScheme {
    val primary = when (theme) {
        ThemePreset.CREAM -> Color(0xFF4D7CFE)
        ThemePreset.SAKURA -> Color(0xFF8765D8)
        ThemePreset.SKY -> Color(0xFF2B9CB6)
        ThemePreset.MINT -> Color(0xFFE55F91)
        ThemePreset.DARK -> Color(0xFFED8B32)
    }
    return if (backgroundStyle == BackgroundStyle.GRAY) lightColorScheme(primary = primary, background = Color(0xFFE7E9ED), surface = Color(0xFFF2F3F5))
    else lightColorScheme(primary = primary, background = Color(0xFFF7F8FC), surface = Color.White)
}

private fun v13CourseColor(seed: String): Color {
    val colors = listOf(Color(0xFFF7B6C8), Color(0xFFB8D8F7), Color(0xFFC5E6C8), Color(0xFFE1C8F2), Color(0xFFF5D5A8), Color(0xFFBDE3E5))
    return colors[abs(seed.hashCode()) % colors.size]
}

private fun v13WidgetTextColor(mode: WidgetTextMode, background: Int, hasImage: Boolean): Color = when (mode) {
    WidgetTextMode.LIGHT -> Color.White
    WidgetTextMode.DARK -> Color(0xFF222222)
    WidgetTextMode.AUTO -> if (hasImage || v13Luminance(background) < 0.5) Color.White else Color(0xFF222222)
}

private fun v13Luminance(color: Int): Double {
    val r = ((color shr 16) and 0xFF) / 255.0
    val g = ((color shr 8) and 0xFF) / 255.0
    val b = (color and 0xFF) / 255.0
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

private fun v13NearestPeriod(minute: Int): Int = v13Periods.indices.minByOrNull { abs(v13Periods[it].start - minute) } ?: 0
private fun v13Minute(value: Int): String = "%02d:%02d".format(value / 60, value % 60)
private fun v13Weekday(day: Int): String = listOf("一", "二", "三", "四", "五", "六", "日")[day.coerceIn(1, 7) - 1]
private fun v13CurrentWeekday(): Int = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
    Calendar.SUNDAY -> 7
    else -> Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
}
private fun v13CurrentWeekDates(): List<String> {
    val cal = Calendar.getInstance()
    val current = v13CurrentWeekday()
    cal.add(Calendar.DAY_OF_MONTH, -(current - 1))
    return (1..7).map {
        val text = SimpleDateFormat("M/d", Locale.getDefault()).format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        text
    }
}
private fun v13FormatTime(value: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
private fun v13TodayStamp(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

private fun v13PickMinute(context: Context, minute: Int, onValue: (Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onValue(h * 60 + m) }, minute / 60, minute % 60, true).show()
}
private fun v13PickDateTime(context: Context, value: Long?, onValue: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = value ?: System.currentTimeMillis() }
    DatePickerDialog(context, { _, y, m, d ->
        val picked = Calendar.getInstance().apply { set(y, m, d, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), 0) }
        TimePickerDialog(context, { _, h, min -> picked.set(Calendar.HOUR_OF_DAY, h); picked.set(Calendar.MINUTE, min); onValue(picked.timeInMillis) }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}
private fun persistUriPermission(context: Context, uri: Uri) {
    runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}
private fun requestNotifyPermission(context: Context, enabled: Boolean, launcher: androidx.activity.result.ActivityResultLauncher<String>) {
    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
