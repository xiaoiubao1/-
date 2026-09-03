package com.xiaoiubao.suixinji

import android.Manifest
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

private enum class V131Section { TIMETABLE, NOTES, SETTINGS }
private data class V131Period(val number: Int, val start: Int, val end: Int)

private val v131Periods = listOf(
    V131Period(1, 8 * 60 + 30, 9 * 60 + 15),
    V131Period(2, 9 * 60 + 25, 10 * 60 + 10),
    V131Period(3, 10 * 60 + 30, 11 * 60 + 15),
    V131Period(4, 11 * 60 + 25, 12 * 60 + 10),
    V131Period(5, 14 * 60 + 30, 15 * 60 + 15),
    V131Period(6, 15 * 60 + 25, 16 * 60 + 10),
    V131Period(7, 17 * 60 + 30, 18 * 60 + 15),
    V131Period(8, 18 * 60 + 25, 19 * 60 + 10)
)

private val LocalV131Glass = staticCompositionLocalOf { 0.60f }
private val LocalV131HasWallpaper = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuixinjiRootV131(
    viewModel: MainViewModel,
    targetEventId: Long,
    targetCourseId: Long,
    settings: AppSettings
) {
    val context = LocalContext.current
    val events by viewModel.events.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val operationMessage by viewModel.importMessage.collectAsState()

    var section by remember { mutableStateOf(V131Section.TIMETABLE) }
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
            v131PersistUriPermission(context, it)
            backgroundUri = it.toString()
            customBackgroundEnabled = true
            settings.wallpaper = backgroundUri
            settings.customBackgroundEnabled = true
        }
    }
    val widgetBackgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            v131PersistUriPermission(context, it)
            widgetBackgroundUri = it.toString()
            settings.widgetBackgroundUri = widgetBackgroundUri
            EventWidgetProvider.updateAll(context)
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { v131PersistUriPermission(context, it); viewModel.importFromUri(it) }
    }
    val csvExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let(viewModel::createBackup)
    }
    val backupRestorer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { v131PersistUriPermission(context, it); pendingRestoreUri = it }
    }

    LaunchedEffect(events, targetEventId) {
        if (!handledEventTarget && targetEventId > 0 && events.isNotEmpty()) {
            events.firstOrNull { it.id == targetEventId }?.let {
                section = V131Section.NOTES
                editingEvent = it
                handledEventTarget = true
            }
        }
    }
    LaunchedEffect(courses, targetCourseId) {
        if (!handledCourseTarget && targetCourseId > 0 && courses.isNotEmpty()) {
            courses.firstOrNull { it.id == targetCourseId }?.let {
                section = V131Section.TIMETABLE
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

    val hasWallpaper = customBackgroundEnabled && backgroundUri.isNotBlank()
    MaterialTheme(colorScheme = v131ColorScheme(theme, backgroundStyle)) {
        CompositionLocalProvider(
            LocalV131Glass provides glassStrength,
            LocalV131HasWallpaper provides hasWallpaper
        ) {
            Box(Modifier.fillMaxSize()) {
                V131Background(backgroundStyle, hasWallpaper, backgroundUri, glassStrength)
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        V131BottomBar(section) { section = it }
                    }
                ) { padding ->
                    when (section) {
                        V131Section.TIMETABLE -> V131Timetable(
                            modifier = Modifier.padding(padding),
                            courses = courses,
                            onEdit = { editingCourse = it },
                            onAdd = { editingCourse = it }
                        )
                        V131Section.NOTES -> V131Notes(
                            modifier = Modifier.padding(padding),
                            events = events,
                            onEdit = { editingEvent = it },
                            onAdd = { editingEvent = EventNote() },
                            onToggle = viewModel::toggleCompleted,
                            onDelete = viewModel::delete
                        )
                        V131Section.SETTINGS -> V131Settings(
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
                            onCsv = { csvExporter.launch("suixinji-events-${v131TodayStamp()}.csv") },
                            onBackup = { backupExporter.launch("suixinji-backup-${v131TodayStamp()}.suixinji") },
                            onRestore = { backupRestorer.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                        )
                    }
                }
            }
        }
    }

    editingEvent?.let { note ->
        V131EventEditor(note, onDismiss = { editingEvent = null }) { updated ->
            v131RequestNotifyPermission(context, updated.reminderEnabled, notificationPermissionLauncher)
            viewModel.save(updated)
            editingEvent = null
        }
    }
    editingCourse?.let { course ->
        V131CourseEditor(
            course = course,
            onDismiss = { editingCourse = null },
            onDelete = { viewModel.deleteCourse(course); editingCourse = null }
        ) { updated ->
            v131RequestNotifyPermission(context, updated.reminderEnabled, notificationPermissionLauncher)
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
            confirmButton = { Button(onClick = { pendingRestoreUri = null; viewModel.restoreBackup(uri) }) { Text("覆盖并恢复") } }
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
private fun V131Background(style: BackgroundStyle, hasWallpaper: Boolean, uri: String, strength: Float) {
    val base = if (style == BackgroundStyle.LIGHT) Color(0xFFF7F8FC) else Color(0xFFE3E6EA)
    Box(Modifier.fillMaxSize().background(base)) {
        if (hasWallpaper) {
            V131UriImage(
                uri = uri,
                modifier = Modifier.fillMaxSize().blur((strength * 7f).dp)
            )
            // v1.3.1 改为非常轻的雾层，不再把壁纸“洗白”。
            Box(
                Modifier.fillMaxSize().background(
                    Color.White.copy(alpha = 0.015f + strength * 0.055f)
                )
            )
        }
    }
}

@Composable
private fun V131BottomBar(selected: V131Section, onSelect: (V131Section) -> Unit) {
    val wallpaper = LocalV131HasWallpaper.current
    Box(
        Modifier
  .fillMaxWidth()
  .navigationBarsPadding()
  .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
  modifier = Modifier.fillMaxWidth().height(66.dp),
  shape = RoundedCornerShape(33.dp),
  color = Color.White.copy(alpha = if (wallpaper) 0.10f else 0.34f),
  border = BorderStroke(
      1.dp,
      Color.White.copy(alpha = if (wallpaper) 0.28f else 0.60f)
  ),
  shadowElevation = if (wallpaper) 0.dp else 2.dp,
  tonalElevation = 0.dp
        ) {
  Row(
      Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
      V131NavItem(
          Modifier.weight(1f),
          selected == V131Section.TIMETABLE,
          Icons.Default.CalendarMonth,
          "课表"
      ) { onSelect(V131Section.TIMETABLE) }
      V131NavItem(
          Modifier.weight(1f),
          selected == V131Section.NOTES,
          Icons.Default.NoteAlt,
          "随心记"
      ) { onSelect(V131Section.NOTES) }
      V131NavItem(
          Modifier.weight(1f),
          selected == V131Section.SETTINGS,
          Icons.Default.Person,
          "我的"
      ) { onSelect(V131Section.SETTINGS) }
  }
        }
    }
}

@Composable
private fun V131NavItem(
    modifier: Modifier,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val wallpaper = LocalV131HasWallpaper.current
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
    }
    val selectedBackground = MaterialTheme.colorScheme.primary.copy(
        alpha = if (wallpaper) 0.18f else 0.13f
    )
    Column(
        modifier = modifier
  .fillMaxHeight()
  .clip(RoundedCornerShape(26.dp))
  .background(if (selected) selectedBackground else Color.Transparent)
  .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(2.dp))
        Text(
  label,
  fontSize = 10.5.sp,
  color = color,
  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
@Composable
private fun V131UriImage(uri: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()
    }
    bitmap?.let { Image(it, null, modifier, contentScale = contentScale) }
}

@Composable
private fun V131GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val strength = LocalV131Glass.current
    val wallpaper = LocalV131HasWallpaper.current
    val alpha = if (wallpaper) (0.16f + strength * 0.12f) else (0.46f + strength * 0.10f)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = alpha.coerceIn(0.16f, 0.56f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (wallpaper) 0.34f else 0.58f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(11.dp), content = content)
    }
}

@Composable
private fun V131Timetable(
    modifier: Modifier,
    courses: List<Course>,
    onEdit: (Course) -> Unit,
    onAdd: (Course) -> Unit
) {
    val currentDay = v131CurrentWeekday()
    val dates = remember { v131CurrentWeekDates() }
    val now = remember { Calendar.getInstance() }
    val dateText = remember { SimpleDateFormat("yyyy/M/d", Locale.getDefault()).format(Date()) }

    Column(modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 7.dp, vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dateText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("第 ${now.get(Calendar.WEEK_OF_YEAR)} 周", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(
                onClick = { onAdd(Course(dayOfWeek = currentDay)) },
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 5.dp)
            ) { Icon(Icons.Default.Add, null, Modifier.size(17.dp)); Spacer(Modifier.width(3.dp)); Text("添加") }
        }
        Spacer(Modifier.height(5.dp))
        V131GlassCard(Modifier.weight(1f).fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val headerHeight = 34.dp
                val rowHeight = ((maxHeight - headerHeight - 3.dp) / 8f).coerceIn(34.dp, 58.dp)
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().height(headerHeight), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(41.dp), contentAlignment = Alignment.Center) { Text("节次", fontSize = 8.sp) }
                        (1..7).forEach { day ->
                            val selected = day == currentDay
                            Box(
                                Modifier.weight(1f).fillMaxHeight().padding(horizontal = 0.5.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.11f) else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(v131Weekday(day), fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                    Text(dates[day - 1], fontSize = 7.sp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    v131Periods.forEachIndexed { index, period ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                        Row(Modifier.fillMaxWidth().height(rowHeight)) {
                            Column(
                                Modifier.width(41.dp).fillMaxHeight(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(period.number.toString(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(v131Minute(period.start), fontSize = 6.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            (1..7).forEach { day ->
                                val matches = courses.filter { it.dayOfWeek == day && v131NearestPeriod(it.startMinute) == index }
                                val course = matches.firstOrNull()
                                Box(
                                    Modifier.weight(1f).fillMaxHeight().padding(0.7.dp)
                                        .clip(RoundedCornerShape(6.dp))
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
                                            shape = RoundedCornerShape(6.dp),
                                            color = v131CourseColor(course.name).copy(alpha = 0.72f)
                                        ) {
                                            Column(
                                                Modifier.fillMaxSize().padding(horizontal = 1.5.dp, vertical = 1.dp),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(course.name, fontSize = 7.5.sp, lineHeight = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                if (course.location.isNotBlank() && rowHeight >= 43.dp) {
                                                    Text(course.location, fontSize = 6.sp, lineHeight = 6.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
}

@Composable
private fun V131Notes(
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
    Column(modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 11.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("随心记", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("简单记录，快速找到", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onAdd) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("记录") }
        }
        Spacer(Modifier.height(7.dp))
        V131GlassCard {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索标题、内容或地点") },
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                FilterChip(filter == EventFilter.ALL, { filter = EventFilter.ALL }, { Text("全部") })
                FilterChip(filter == EventFilter.UPCOMING, { filter = EventFilter.UPCOMING }, { Text("待办") })
                FilterChip(filter == EventFilter.COMPLETED, { filter = EventFilter.COMPLETED }, { Text("完成") })
            }
        }
        Spacer(Modifier.height(7.dp))
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无记录") }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(visible, key = { it.id }) { note ->
                    V131GlassCard(Modifier.fillMaxWidth().clickable { onEdit(note) }) {
                        if (note.imageUri.isNotBlank()) {
                            V131UriImage(note.imageUri, Modifier.fillMaxWidth().height(105.dp).clip(RoundedCornerShape(12.dp)))
                            Spacer(Modifier.height(7.dp))
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(note.title.ifBlank { "未命名记录" }, fontWeight = FontWeight.Bold, textDecoration = if (note.completed) TextDecoration.LineThrough else null)
                                if (note.details.isNotBlank()) Text(note.details, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                                note.eventTime?.let { Text(v131FormatTime(it), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary) }
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
private fun V131Settings(
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

    LazyColumn(
        modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item {
            Text("我的", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("外观、桌面小组件、数据与提醒", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            V131GlassCard {
                V131SettingTitle(Icons.Default.Wallpaper, "应用背景", "新版玻璃层更薄，壁纸会明显透出来")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用自定义背景", Modifier.weight(1f)); Switch(customBackgroundEnabled, onCustomBackground)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    BackgroundStyle.entries.forEach { style -> FilterChip(style == backgroundStyle, { onBackgroundStyle(style) }, { Text(style.title) }) }
                }
                OutlinedButton(onClick = onPickBackground, enabled = customBackgroundEnabled, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Photo, null); Spacer(Modifier.width(4.dp)); Text(if (hasBackground) "更换背景图片" else "选择背景图片")
                }
                Text("雾化强度 ${(glassStrength * 100).toInt()}%", fontSize = 11.sp)
                Slider(value = glassStrength, onValueChange = onGlassStrength)
                Text("卡片透明度已改为约 16%～28%，底部导航约 10%。", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            V131GlassCard {
                V131SettingTitle(Icons.Default.Palette, "主题色", "选择界面强调色")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ThemePreset.entries.forEach { preset -> FilterChip(preset == theme, { onTheme(preset) }, { Text(preset.title) }) }
                }
            }
        }
        item {
            V131GlassCard {
                V131SettingTitle(Icons.Default.Widgets, "桌面小组件 DIY", "背景图、颜色、文字、透明度与磨砂")
                OutlinedButton(onClick = onPickWidgetBackground, modifier = Modifier.fillMaxWidth()) { Text(if (widgetBackgroundUri.isBlank()) "选择小组件背景图" else "更换小组件背景图") }
                if (widgetBackgroundUri.isNotBlank()) TextButton(onClick = onClearWidgetBackground) { Text("移除背景图") }
                Text("背景色", fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    backgroundColors.forEach { c -> V131ColorDot(c, c == widgetBackgroundColor) { onWidgetBackgroundColor(c) } }
                }
                Spacer(Modifier.height(4.dp))
                Text("文字", fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    WidgetTextMode.entries.forEach { m -> FilterChip(m == widgetTextMode, { onWidgetTextMode(m) }, { Text(m.title) }) }
                }
                Text("强调色", fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accentColors.forEach { c -> V131ColorDot(c, c == widgetAccentColor) { onWidgetAccentColor(c) } }
                }
                Text("组件透明度 ${(widgetOpacity * 100).toInt()}%", fontSize = 11.sp)
                Slider(value = widgetOpacity, onValueChange = onWidgetOpacity, valueRange = 0.35f..1f)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("磨砂覆盖", Modifier.weight(1f)); Switch(widgetFrosted, onWidgetFrosted) }
            }
        }
        item {
            V131GlassCard {
                V131SettingTitle(Icons.Default.NotificationsActive, "通知与数据", "测试提醒、导入导出与完整备份")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = onNotificationTest) { Text("测试通知") }
                    OutlinedButton(onClick = onImport) { Text("导入") }
                    OutlinedButton(onClick = onCsv) { Text("CSV") }
                    Button(onClick = onBackup) { Text("备份") }
                    OutlinedButton(onClick = onRestore) { Text("恢复") }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun V131SettingTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(7.dp))
        Column { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun V131ColorDot(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color(color)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { if (selected) Icon(Icons.Default.Check, null, tint = if (v131Luminance(color) < 0.5) Color.White else Color.Black, modifier = Modifier.size(17.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V131EventEditor(note: EventNote, onDismiss: () -> Unit, onSave: (EventNote) -> Unit) {
    val context = LocalContext.current
    var title by remember(note.id) { mutableStateOf(note.title) }
    var details by remember(note.id) { mutableStateOf(note.details) }
    var location by remember(note.id) { mutableStateOf(note.location) }
    var eventTime by remember(note.id) { mutableStateOf(note.eventTime) }
    var reminder by remember(note.id) { mutableStateOf(note.reminderEnabled) }
    var imageUri by remember(note.id) { mutableStateOf(note.imageUri) }
    var showDateTime by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { v131PersistUriPermission(context, it); imageUri = it.toString() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (note.id == 0L) "新建随心记" else "编辑随心记") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("标题") })
                OutlinedTextField(details, { details = it }, Modifier.fillMaxWidth(), label = { Text("内容") }, minLines = 3)
                OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("地点") })
                FilledTonalButton(onClick = { showDateTime = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(6.dp)); Text(eventTime?.let(::v131FormatTime) ?: "选择日期和时间")
                }
                if (eventTime != null) TextButton(onClick = { eventTime = null }) { Text("清除时间") }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("到点提醒", Modifier.weight(1f)); Switch(reminder, { reminder = it }) }
                OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text(if (imageUri.isBlank()) "添加图片" else "更换图片") }
                if (imageUri.isNotBlank()) V131UriImage(imageUri, Modifier.fillMaxWidth().height(115.dp).clip(RoundedCornerShape(12.dp)))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onSave(note.copy(title = title.trim(), details = details.trim(), location = location.trim(), eventTime = eventTime, reminderEnabled = reminder, imageUri = imageUri)) },
                enabled = title.isNotBlank()
            ) { Text("保存") }
        }
    )

    if (showDateTime) {
        V131DateTimeSheet(
            initial = eventTime,
            onDismiss = { showDateTime = false },
            onConfirm = { eventTime = it; showDateTime = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V131CourseEditor(course: Course, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Course) -> Unit) {
    var name by remember(course.id) { mutableStateOf(course.name) }
    var teacher by remember(course.id) { mutableStateOf(course.teacher) }
    var location by remember(course.id) { mutableStateOf(course.location) }
    var day by remember(course.id) { mutableIntStateOf(course.dayOfWeek.coerceIn(1, 7)) }
    var start by remember(course.id) { mutableIntStateOf(course.startMinute) }
    var end by remember(course.id) { mutableIntStateOf(course.endMinute) }
    var note by remember(course.id) { mutableStateOf(course.note) }
    var reminder by remember(course.id) { mutableStateOf(course.reminderEnabled) }
    var before by remember(course.id) { mutableIntStateOf(course.reminderMinutesBefore) }
    var editingStart by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (course.id == 0L) "添加课程" else "编辑课程") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("课程名称") })
                OutlinedTextField(teacher, { teacher = it }, Modifier.fillMaxWidth(), label = { Text("老师") })
                OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("教室") })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { d -> FilterChip(d == day, { day = d }, { Text(v131Weekday(d)) }) }
                }
                Text("快捷课节", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    v131Periods.forEach { p ->
                        AssistChip(onClick = { start = p.start; end = p.end }, label = { Text("${p.number}节 ${v131Minute(p.start)}") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = { editingStart = true }, Modifier.weight(1f)) { Text("开始 ${v131Minute(start)}") }
                    FilledTonalButton(onClick = { editingStart = false }, Modifier.weight(1f)) { Text("结束 ${v131Minute(end)}") }
                }
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("备注") })
                Row(verticalAlignment = Alignment.CenterVertically) { Text("课程开始提醒", Modifier.weight(1f)); Switch(reminder, { reminder = it }) }
                if (reminder) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0, 5, 10, 15, 30, 60).forEach { m -> FilterChip(m == before, { before = m }, { Text(if (m == 0) "上课时" else "$m 分") }) }
                    }
                }
                if (course.id != 0L) OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("删除课程") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(course.copy(name = name.trim(), teacher = teacher.trim(), location = location.trim(), dayOfWeek = day, startMinute = start, endMinute = end, note = note.trim(), reminderEnabled = reminder, reminderMinutesBefore = before)) },
                enabled = name.isNotBlank() && end > start
            ) { Text("保存") }
        }
    )

    editingStart?.let { isStart ->
        V131MinuteSheet(
            title = if (isStart) "选择开始时间" else "选择结束时间",
            initial = if (isStart) start else end,
            onDismiss = { editingStart = null },
            onConfirm = {
                if (isStart) start = it else end = it
                editingStart = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V131DateTimeSheet(initial: Long?, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val initialCalendar = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial ?: System.currentTimeMillis() } }
    var selectedDay by remember(initial) { mutableLongStateOf(v131StartOfDay(initialCalendar.timeInMillis)) }
    var hour by remember(initial) { mutableIntStateOf(initialCalendar.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember(initial) { mutableIntStateOf((initialCalendar.get(Calendar.MINUTE) / 5) * 5) }
    var showCalendar by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 18.dp)) {
            Text("选择日期和时间", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("常用日期和时间直接点，远期日期再展开日历。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items((0..9).toList()) { offset ->
                    val dayMillis = v131DayOffset(offset)
                    val selected = v131SameDay(selectedDay, dayMillis)
                    FilterChip(
                        selected = selected,
                        onClick = { selectedDay = dayMillis; showCalendar = false },
                        label = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(when (offset) { 0 -> "今天"; 1 -> "明天"; 2 -> "后天"; else -> v131WeekdayFromMillis(dayMillis) }, fontSize = 10.sp)
                                Text(SimpleDateFormat("M/d", Locale.getDefault()).format(Date(dayMillis)), fontSize = 9.sp)
                            }
                        }
                    )
                }
                item { AssistChip(onClick = { showCalendar = !showCalendar }, label = { Text(if (showCalendar) "收起日历" else "更多日期") }, leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp)) }) }
            }

            if (showCalendar) {
                val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDay)
                DatePicker(state = datePickerState, showModeToggle = false)
                LaunchedEffect(datePickerState.selectedDateMillis) {
                    datePickerState.selectedDateMillis?.let { selectedDay = v131StartOfDay(it) }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("快捷时间", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(8 to 0, 9 to 0, 10 to 0, 12 to 0, 14 to 0, 18 to 0, 20 to 0, 22 to 0).forEach { (h, m) ->
                    AssistChip(onClick = { hour = h; minute = m }, label = { Text("%02d:%02d".format(h, m)) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("小时", fontSize = 11.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items((0..23).toList()) { h -> FilterChip(h == hour, { hour = h }, { Text("%02d".format(h)) }) }
            }
            Text("分钟", fontSize = 11.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items((0..55 step 5).toList()) { m -> FilterChip(m == minute, { minute = m }, { Text("%02d".format(m)) }) }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(6.dp))
                Button(onClick = { onConfirm(v131ComposeDateTime(selectedDay, hour, minute)) }) { Text("确定") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V131MinuteSheet(title: String, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var hour by remember(initial) { mutableIntStateOf((initial / 60).coerceIn(0, 23)) }
    var minute by remember(initial) { mutableIntStateOf((initial % 60 / 5) * 5) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("直接点小时和分钟，比系统时钟盘更快。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text("快捷", fontSize = 11.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(8 * 60 + 30, 9 * 60 + 15, 9 * 60 + 25, 10 * 60 + 10, 10 * 60 + 30, 11 * 60 + 15, 14 * 60 + 30, 15 * 60 + 15, 17 * 60 + 30, 18 * 60 + 15).forEach { value ->
                    AssistChip(onClick = { hour = value / 60; minute = value % 60 }, label = { Text(v131Minute(value)) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("小时", fontSize = 11.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items((6..23).toList()) { h -> FilterChip(h == hour, { hour = h }, { Text("%02d".format(h)) }) }
            }
            Text("分钟", fontSize = 11.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items((0..55 step 5).toList()) { m -> FilterChip(m == minute, { minute = m }, { Text("%02d".format(m)) }) }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(6.dp))
                Button(onClick = { onConfirm(hour * 60 + minute) }) { Text("确定") }
            }
        }
    }
}

private fun v131ColorScheme(theme: ThemePreset, backgroundStyle: BackgroundStyle): ColorScheme {
    val primary = when (theme) {
        ThemePreset.CREAM -> Color(0xFF4D7CFE)
        ThemePreset.SAKURA -> Color(0xFF8765D8)
        ThemePreset.SKY -> Color(0xFF2B9CB6)
        ThemePreset.MINT -> Color(0xFFE55F91)
        ThemePreset.DARK -> Color(0xFFED8B32)
    }
    return if (backgroundStyle == BackgroundStyle.GRAY) lightColorScheme(primary = primary, background = Color(0xFFE3E6EA), surface = Color(0xFFF3F4F6))
    else lightColorScheme(primary = primary, background = Color(0xFFF7F8FC), surface = Color.White)
}

private fun v131CourseColor(seed: String): Color {
    val colors = listOf(Color(0xFFF7B6C8), Color(0xFFB8D8F7), Color(0xFFC5E6C8), Color(0xFFE1C8F2), Color(0xFFF5D5A8), Color(0xFFBDE3E5))
    return colors[abs(seed.hashCode()) % colors.size]
}

private fun v131Luminance(color: Int): Double {
    val r = ((color shr 16) and 0xFF) / 255.0
    val g = ((color shr 8) and 0xFF) / 255.0
    val b = (color and 0xFF) / 255.0
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

private fun v131NearestPeriod(minute: Int): Int = v131Periods.indices.minByOrNull { abs(v131Periods[it].start - minute) } ?: 0
private fun v131Minute(value: Int): String = "%02d:%02d".format(value / 60, value % 60)
private fun v131Weekday(day: Int): String = listOf("一", "二", "三", "四", "五", "六", "日")[day.coerceIn(1, 7) - 1]
private fun v131CurrentWeekday(): Int = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
    Calendar.SUNDAY -> 7
    else -> Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
}
private fun v131CurrentWeekDates(): List<String> {
    val cal = Calendar.getInstance()
    val current = v131CurrentWeekday()
    cal.add(Calendar.DAY_OF_MONTH, -(current - 1))
    return (1..7).map {
        val text = SimpleDateFormat("M/d", Locale.getDefault()).format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        text
    }
}
private fun v131FormatTime(value: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
private fun v131TodayStamp(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
private fun v131StartOfDay(value: Long): Long = Calendar.getInstance().apply {
    timeInMillis = value
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
private fun v131DayOffset(offset: Int): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_MONTH, offset)
}.timeInMillis
private fun v131SameDay(a: Long, b: Long): Boolean = v131StartOfDay(a) == v131StartOfDay(b)
private fun v131WeekdayFromMillis(value: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = value }
    val day = when (cal.get(Calendar.DAY_OF_WEEK)) { Calendar.SUNDAY -> 7; else -> cal.get(Calendar.DAY_OF_WEEK) - 1 }
    return "周${v131Weekday(day)}"
}
private fun v131ComposeDateTime(day: Long, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
    timeInMillis = day
    set(Calendar.HOUR_OF_DAY, hour)
    set(Calendar.MINUTE, minute)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
private fun v131PersistUriPermission(context: Context, uri: Uri) {
    runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}
private fun v131RequestNotifyPermission(context: Context, enabled: Boolean, launcher: androidx.activity.result.ActivityResultLauncher<String>) {
    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
