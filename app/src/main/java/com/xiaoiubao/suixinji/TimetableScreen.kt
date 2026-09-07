package com.xiaoiubao.suixinji

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoiubao.suixinji.data.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

internal fun weekday(day: Int) = "周${"一二三四五六日"[day - 1]}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val courses by viewModel.courses.collectAsState()
    val semesters by viewModel.semesters.collectAsState()
    val allPeriods by viewModel.periods.collectAsState()
    val activeId by viewModel.activeSemesterId.collectAsState()
    val preview by viewModel.coursePreview.collectAsState()
    val term = semesters.firstOrNull { it.id == activeId }
    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(30_000) }
    }
    var semesterMenu by remember { mutableStateOf(false) }
    var importMenu by remember { mutableStateOf(false) }
    var fileTarget by rememberSaveable { mutableLongStateOf(activeId) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.previewCourseFile(it, fileTarget) }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportCourses(it, fileTarget) }
    }
    if (term == null) { Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val periods = allPeriods.filter { it.semesterId == term.id }
    val termCourses = courses.filter { it.semesterId == term.id }
    val currentWeek = term.weekOn(now.toLocalDate())
    var selectedWeek by rememberSaveable(term.id) { mutableIntStateOf((currentWeek ?: 1).coerceIn(1, term.totalWeeks)) }
    LaunchedEffect(term.totalWeeks) { selectedWeek = selectedWeek.coerceIn(1, term.totalWeeks) }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var weekends by rememberSaveable { mutableStateOf(true) }
    var search by rememberSaveable { mutableStateOf("") }
    fun newCourse(day: Int = now.dayOfWeek.value, period: CoursePeriod? = periods.firstOrNull()) {
        viewModel.editingCourse = Course(semesterId = term.id, weeks = (1..term.totalWeeks).toList(),
            dayOfWeek = day, startMinute = period?.startMinute ?: 480, endMinute = period?.endMinute ?: 525)
    }
    val weekCourses = termCourses.filter { currentWeek == null || selectedWeek in it.weeks }
    val todayCourses = termCourses.filter { Timetable.occursOn(it, term, now.toLocalDate()) }
    val next = todayCourses.firstOrNull { it.endMinute > now.hour * 60 + now.minute }
    val firstDate = if (term.startDate.isBlank()) null else LocalDate.parse(term.startDate).plusWeeks((selectedWeek - 1).toLong())

    LazyColumn(modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("我的课表", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Box {
                        TextButton(onClick = { semesterMenu = true }, contentPadding = PaddingValues(0.dp)) {
                            Text(term.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 230.dp))
                            Icon(Icons.Default.ExpandMore, "切换学期", Modifier.size(18.dp))
                        }
                        DropdownMenu(semesterMenu, { semesterMenu = false }) {
                            semesters.forEach { semester -> DropdownMenuItem(text = { Text(semester.name) }, onClick = { semesterMenu = false; viewModel.selectSemester(semester.id) }) }
                            DropdownMenuItem(text = { Text("＋ 新建学期") }, onClick = { semesterMenu = false; viewModel.editingSemester = SemesterDraft(Semester.newTerm()) })
                        }
                    }
                }
                IconButton(onClick = { viewModel.editingSemester = SemesterDraft.from(term, periods) }) { Icon(Icons.Default.Tune, "学期与作息设置") }
                FilledIconButton(onClick = { newCourse() }) { Icon(Icons.Default.Add, "添加课程") }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .83f)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${now.monthValue} 月 ${now.dayOfMonth} 日 · ${weekday(now.dayOfWeek.value)}" + when {
                        currentWeek == null -> " · 每周重复"
                        currentWeek < 1 -> " · 尚未开学"
                        currentWeek > term.totalWeeks -> " · 学期已结束"
                        else -> " · 第 $currentWeek 周"
                    }, style = MaterialTheme.typography.labelLarge)
                    Text(next?.name ?: if (todayCourses.isEmpty()) "今天没有课程" else "今天的课程已结束", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (next != null) Text("${Timetable.time(next.startMinute)}–${Timetable.time(next.endMinute)}  ${next.location}", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { mode = 1 }, contentPadding = PaddingValues(0.dp)) { Text("查看今日 ${todayCourses.size} 条安排 →") }
                }
            }
        }
        if (term.startDate.isBlank()) item {
            TextButton(onClick = { viewModel.editingSemester = SemesterDraft.from(term, periods) }) {
                Text("设置第一周起始日，启用周次和单双周。当前仍按每周重复。", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("周课表", "今天", "全部课程").forEachIndexed { index, title -> FilterChip(mode == index, { mode = index }, { Text(title) }) }
                Box {
                    OutlinedButton(onClick = { importMenu = true }) { Icon(Icons.Default.Download, null, Modifier.size(17.dp)); Text("导入 / 导出") }
                    DropdownMenu(importMenu, { importMenu = false }) {
                        DropdownMenuItem(text = { Text("教务网站导入（通用识别）") }, onClick = { importMenu = false; viewModel.showSchoolBrowser = true })
                        DropdownMenuItem(text = { Text("导入课程 CSV / HTML") }, onClick = { importMenu = false; fileTarget = term.id; importer.launch(arrayOf("text/csv", "text/html", "text/plain", "application/octet-stream", "*/*")) })
                        DropdownMenuItem(text = { Text("导出本学期课程 CSV") }, onClick = { importMenu = false; fileTarget = term.id; exporter.launch("suixinji-courses-${now.toLocalDate()}.csv") })
                    }
                }
            }
        }
        if (mode == 0) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedWeek-- }, enabled = currentWeek != null && selectedWeek > 1) { Icon(Icons.Default.ChevronLeft, "上一周") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (currentWeek == null) "每周课表" else "第 $selectedWeek 周 / ${term.totalWeeks} 周", fontWeight = FontWeight.Bold)
                        firstDate?.let { Text("${it.monthValue}/${it.dayOfMonth} – ${it.plusDays(6).monthValue}/${it.plusDays(6).dayOfMonth}", style = MaterialTheme.typography.labelSmall) }
                    }
                    IconButton(onClick = { selectedWeek++ }, enabled = currentWeek != null && selectedWeek < term.totalWeeks) { Icon(Icons.Default.ChevronRight, "下一周") }
                    TextButton(onClick = { selectedWeek = (currentWeek ?: 1).coerceIn(1, term.totalWeeks) }) { Text("本周") }
                }
                if (currentWeek != null) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    (1..term.totalWeeks).forEach { week -> FilterChip(week == selectedWeek, { selectedWeek = week }, { Text("$week") }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(weekends, { weekends = it }); Text("显示周末", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f)); Text("${weekCourses.size} 条安排 · 左右滑动", style = MaterialTheme.typography.labelSmall)
                }
                if (!weekends && weekCourses.any { it.dayOfWeek > 5 }) TextButton(onClick = { weekends = true }) { Text("周末还有课程，点击显示") }
            }
            item {
                WeekGrid(weekCourses, periods, weekends, firstDate, now.toLocalDate(), { viewModel.editingCourse = it }, { day, p -> newCourse(day, p) })
            }
            val outside = weekCourses.filter { course -> periods.none { course.startMinute < it.endMinute && course.endMinute > it.startMinute } }
            if (outside.isNotEmpty()) item { Text("作息节次以外的课程", fontWeight = FontWeight.Bold) }
            items(outside, key = { "outside-${it.id}" }) { CourseListCard(it, { viewModel.editingCourse = it }) }
        } else {
            if (mode == 2) item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("搜索课程、教师或教室") }, leadingIcon = { Icon(Icons.Default.Search, null) }) }
            val shown = if (mode == 1) todayCourses else termCourses.filter { search.isBlank() || listOf(it.name, it.teacher, it.location).any { value -> value.contains(search, ignoreCase = true) } }
            if (shown.isEmpty()) item { Text(if (mode == 1) "今天可以自由安排时间。" else "暂无课程。可以手动添加，或从教务网页 / 课程文件导入。", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(shown, key = { it.id }) { course -> CourseListCard(course, { viewModel.editingCourse = course }) }
        }
    }
    viewModel.editingSemester?.let { draft ->
        SemesterEditor(draft, { viewModel.editingSemester = it }, { viewModel.editingSemester = null }, viewModel::saveSemester,
            if (draft.original.id != 0L && semesters.size > 1) ({ viewModel.deleteSemester(draft.original.id) }) else null)
    }
    preview?.let { CoursePreviewDialog(it, termName = semesters.firstOrNull { s -> s.id == it.semesterId }?.name.orEmpty(), onDismiss = viewModel::discardCoursePreview, onConfirm = { selected, replace -> viewModel.confirmCourseImport(it, selected, replace) }) }
    if (viewModel.showSchoolBrowser) SchoolImportScreen(viewModel, term.id)
}

@Composable
private fun WeekGrid(courses: List<Course>, periods: List<CoursePeriod>, weekends: Boolean, monday: LocalDate?, today: LocalDate, onEdit: (Course) -> Unit, onAdd: (Int, CoursePeriod) -> Unit) {
    val days = if (weekends) 1..7 else 1..5
    val colors = listOf(Color(0xFFDCE8FF), Color(0xFFEADFFF), Color(0xFFD8F0ED), Color(0xFFFFE2DF), Color(0xFFFFEEC9))
    Column(Modifier.horizontalScroll(rememberScrollState()).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = .7f)).padding(5.dp)) {
        Row {
            Box(Modifier.width(42.dp).height(44.dp), contentAlignment = Alignment.Center) { Text("节次", fontSize = 11.sp) }
            days.forEach { day ->
                val date = monday?.plusDays((day - 1).toLong())
                Column(Modifier.width(70.dp).height(44.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(weekday(day), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if (date != null) Text("${date.monthValue}/${date.dayOfMonth}", fontSize = 10.sp)
                }
            }
        }
        periods.forEach { period ->
            Row {
                Column(Modifier.width(42.dp).height(116.dp).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(period.number.toString(), fontWeight = FontWeight.Bold)
                    Text(Timetable.time(period.startMinute), fontSize = 9.sp)
                    Text(Timetable.time(period.endMinute), fontSize = 9.sp)
                }
                days.forEach { day ->
                    val matching = courses.filter { it.dayOfWeek == day && it.startMinute < period.endMinute && it.endMinute > period.startMinute }
                    Column(Modifier.width(70.dp).height(116.dp).padding(2.dp).clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .3f)).clickable(enabled = matching.isEmpty()) { onAdd(day, period) }
                        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        matching.forEach { course ->
                            val color = colors[DateTimes.colorIndex(course.name, colors.size)]
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(color).clickable { onEdit(course) }.padding(5.dp)) {
                                val first = periods.firstOrNull { course.startMinute < it.endMinute && course.endMinute > it.startMinute }
                                Text((if (first?.number != period.number) "续 · " else "") + course.name, color = Color(0xFF263047), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                if (course.location.isNotBlank()) Text(course.location, fontSize = 10.sp, color = Color(0xFF445168), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(Timetable.time(course.startMinute), fontSize = 9.sp, color = Color(0xFF445168))
                            }
                        }
                        if (matching.size > 1) Text("${matching.size} 门 · 可上下滑", fontSize = 9.sp, modifier = Modifier.padding(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseListCard(course: Course, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .85f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row { Text(course.name, Modifier.weight(1f), fontWeight = FontWeight.Bold); if (course.reminderEnabled) Icon(Icons.Default.NotificationsActive, "已开启提醒", Modifier.size(17.dp)) }
            Text("${weekday(course.dayOfWeek)} · ${Timetable.time(course.startMinute)}–${Timetable.time(course.endMinute)}", style = MaterialTheme.typography.bodyMedium)
            Text(listOf(course.location, course.teacher).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "地点和教师待补充" }, style = MaterialTheme.typography.bodySmall)
            Text("第 ${Timetable.weeksText(course.weeks)} 周", style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SemesterEditor(draft: SemesterDraft, onChange: (SemesterDraft) -> Unit, onDismiss: () -> Unit, onSave: (SemesterDraft) -> Unit, onDelete: (() -> Unit)?) {
    val context = LocalContext.current
    var deleteConfirm by rememberSaveable { mutableStateOf(false) }
    val error = runCatching { draft.parse() }.exceptionOrNull()?.message
    AlertDialog(onDismissRequest = onDismiss, title = { Text("学期与作息") }, text = {
        Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft.name, { onChange(draft.copy(name = it)) }, Modifier.fillMaxWidth(), label = { Text("学期名称") }, singleLine = true)
            OutlinedTextField(draft.startDate, { onChange(draft.copy(startDate = it)) }, Modifier.fillMaxWidth(), label = { Text("第一周周一 · YYYY-MM-DD") }, singleLine = true,
                trailingIcon = { IconButton(onClick = {
                    val initial = runCatching { LocalDate.parse(draft.startDate) }.getOrDefault(LocalDate.now())
                    android.app.DatePickerDialog(context, { _, y, m, d -> onChange(draft.copy(startDate = LocalDate.of(y, m + 1, d).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString())) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
                }) { Icon(Icons.Default.CalendarMonth, "选择日期，自动取所在周一") } })
            Text("日期留空表示每周重复；填写后按上课周次显示和提醒。日历选择会取所在周的周一。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(draft.totalWeeks, { onChange(draft.copy(totalWeeks = it.take(2))) }, Modifier.fillMaxWidth(), label = { Text("学期总周数 · 1–60") }, singleLine = true)
            Text("每日作息", fontWeight = FontWeight.Bold)
            Text("时间格式 HH:mm。调整作息不会移动已有课程的钟点时间。", style = MaterialTheme.typography.bodySmall)
            draft.periods.forEachIndexed { index, period ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${index + 1}", Modifier.width(18.dp))
                    OutlinedTextField(period.start, { value -> onChange(draft.copy(periods = draft.periods.mapIndexed { i, p -> if (i == index) p.copy(start = value) else p })) }, Modifier.weight(1f), singleLine = true, label = { Text("开始") })
                    OutlinedTextField(period.end, { value -> onChange(draft.copy(periods = draft.periods.mapIndexed { i, p -> if (i == index) p.copy(end = value) else p })) }, Modifier.weight(1f), singleLine = true, label = { Text("结束") })
                }
            }
            Row {
                TextButton(onClick = { onChange(draft.copy(periods = draft.periods + PeriodDraft("", ""))) }, enabled = draft.periods.size < 24) { Text("＋ 添加节次") }
                TextButton(onClick = { onChange(draft.copy(periods = draft.periods.dropLast(1))) }, enabled = draft.periods.size > 1) { Text("移除末节") }
            }
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (onDelete != null) TextButton(onClick = { deleteConfirm = true }) { Text("删除此学期及其课程", color = MaterialTheme.colorScheme.error) }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = { Button(onClick = { onSave(draft) }, enabled = error == null) { Text("保存") } })
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false }, title = { Text("删除整个学期？") }, text = { Text("该学期的所有课程和提醒都会删除。此操作无法撤销。") }, dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("取消") } }, confirmButton = { Button(onClick = { deleteConfirm = false; onDelete?.invoke() }) { Text("删除") } })
}

@Composable
private fun CoursePreviewDialog(preview: CourseImportPreview, termName: String, onDismiss: () -> Unit, onConfirm: (Set<Int>, Boolean) -> Unit) {
    var selected by rememberSaveable(preview) { mutableStateOf(preview.courses.indices.toList()) }
    var replace by rememberSaveable(preview) { mutableStateOf(false) }
    var checked by rememberSaveable(preview) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("核对导入课表") }, text = {
        LazyColumn(Modifier.heightIn(max = 550.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("导入到：$termName", fontWeight = FontWeight.Bold)
                Text("识别 ${preview.courses.size} 条 · 选择 ${selected.size} 条 · 未识别 ${preview.skippedRows} 项")
                Text("${preview.duplicateCount} 条已存在 · ${preview.conflictCount} 条可能时间冲突", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                Row { TextButton(onClick = { selected = preview.courses.indices.toList() }) { Text("全选") }; TextButton(onClick = { selected = emptyList() }) { Text("清空选择") } }
            }
            items(preview.warnings) { warning -> Text(warning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            itemsIndexed(preview.courses) { index, course ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(index in selected, { enabled -> selected = if (enabled) selected + index else selected - index })
                    Column(Modifier.weight(1f)) {
                        Text(course.name, fontWeight = FontWeight.Bold)
                        Text("${weekday(course.dayOfWeek)} ${Timetable.time(course.startMinute)}–${Timetable.time(course.endMinute)}", style = MaterialTheme.typography.bodySmall)
                        Text("第 ${Timetable.weeksText(course.weeks)} 周 · ${course.location.ifBlank { "地点待补充" }}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(replace, { replace = it }, enabled = preview.skippedRows == 0); Text("替换此学期全部课程", style = MaterialTheme.typography.bodySmall) }
                Text(if (replace) "确认后删除此学期的旧课程及提醒，再导入勾选内容。" else "追加导入，保留现有课程并跳过完全重复的安排。", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, { checked = it }); Text("我已核对周次、作息时间和提示", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = { Button(onClick = { onConfirm(selected.toSet(), replace) }, enabled = checked && selected.isNotEmpty()) { Text(if (replace) "替换并导入" else "确认导入") } })
}
