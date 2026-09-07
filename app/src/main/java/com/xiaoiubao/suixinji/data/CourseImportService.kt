package com.xiaoiubao.suixinji.data

import android.content.Context
import android.net.Uri
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter

// Parsed rows are held in memory for review; parsing never writes to the database.
data class CourseImportPreview(
    val semesterId: Long, val source: String, val courses: List<Course>,
    val warnings: List<String> = emptyList(), val skippedRows: Int = 0,
    val duplicateCount: Int = 0, val conflictCount: Int = 0
)

class CourseImportService(private val context: Context) {
    fun read(uri: Uri, semester: Semester, periods: List<CoursePeriod>): CourseImportPreview {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(output.size() + n <= MAX_BYTES) { "课表文件超过 2 MiB，请只导出课表页面" }
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        } ?: error("无法读取课表文件")
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        return if (text.trimStart().startsWith("<")) {
            // Respect the charset declared by saved school HTML, including GBK.
            val html = Jsoup.parse(ByteArrayInputStream(bytes), null, "").outerHtml()
            parseHtml(html, semester, periods)
        } else parseCsv(text, semester, periods)
    }

    fun export(uri: Uri, courses: List<Course>): String {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                writer.write("\uFEFFname,teacher,location,day,startTime,endTime,weeks,note\r\n")
                courses.forEach { course ->
                    writer.write(listOf(course.name, course.teacher, course.location, course.dayOfWeek.toString(),
                        Timetable.time(course.startMinute), Timetable.time(course.endMinute), Timetable.weeksText(course.weeks), course.note)
                        .joinToString(",", postfix = "\r\n") { CsvCodec.escape(it) })
                }
            }
        } ?: error("无法创建课程 CSV")
        return "已导出 ${courses.size} 条课程安排。CSV 不包含学期日期和作息，请用完整备份保留全部设置。"
    }

    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        private val aliases = mapOf(
            "name" to setOf("name", "课程", "课程名", "课程名称", "教学课程"),
            "teacher" to setOf("teacher", "教师", "任课教师", "授课教师"),
            "location" to setOf("location", "教室", "上课地点", "地点"),
            "day" to setOf("day", "dayofweek", "星期", "上课星期", "星期几"),
            "start" to setOf("starttime", "开始时间", "上课时间"),
            "end" to setOf("endtime", "结束时间", "下课时间"),
            "weeks" to setOf("weeks", "周次", "上课周次", "教学周"),
            "periods" to setOf("periods", "节次", "上课节次"),
            "note" to setOf("note", "备注")
        )
        private fun header(value: String): String? {
            val normalized = value.trim().lowercase(java.util.Locale.ROOT).replace(Regex("[\\s：:]"), "")
            return aliases.entries.firstOrNull { normalized in it.value }?.key
        }
        private fun day(value: String): Int {
            val clean = value.trim().replace("星期", "").replace("周", "").replace("礼拜", "")
            return (clean.toIntOrNull() ?: (listOf("一", "二", "三", "四", "五", "六", "日").indexOf(clean) + 1).let { if (clean == "天") 7 else it })
                .also { require(it in 1..7) { "无法识别星期：$value" } }
        }
        private fun times(value: String, periods: List<CoursePeriod>): Pair<Int, Int> {
            val numbers = Timetable.parseWeeks(value.replace("节", ""), periods.size)
            require(numbers == (numbers.first()..numbers.last()).toList()) { "不连续节次请拆成多条课程" }
            return periods.first { it.number == numbers.first() }.startMinute to periods.first { it.number == numbers.last() }.endMinute
        }
        private fun rowCourse(row: List<String>, headers: Map<String, Int>, semester: Semester, periods: List<CoursePeriod>): Course {
            fun value(key: String) = headers[key]?.let { row.getOrNull(it)?.trim() }.orEmpty()
            val (start, end) = if (value("start").isNotBlank() && value("end").isNotBlank())
                Timetable.parseTime(value("start")) to Timetable.parseTime(value("end")) else times(value("periods"), periods)
            return Course(name = value("name"), teacher = value("teacher"), location = value("location"),
                dayOfWeek = day(value("day")), startMinute = start, endMinute = end, note = value("note"),
                semesterId = semester.id, weeks = Timetable.parseWeeks(value("weeks"), semester.totalWeeks)).also { it.validate(semester) }
        }
        fun parseCsv(text: String, semester: Semester, periods: List<CoursePeriod>): CourseImportPreview {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "课程文件过大" }
            val rows = CsvCodec.parse(text)
            require(rows.isNotEmpty()) { "课程文件为空" }
            val headers = rows.first().mapIndexedNotNull { index, value -> header(value)?.let { it to index } }.toMap()
            require(headers.keys.containsAll(listOf("name", "day", "weeks")) &&
                (headers.keys.containsAll(listOf("start", "end")) || "periods" in headers)) {
                "这不是课程 CSV。表头需要：课程名称、星期、开始时间、结束时间、周次；也可用节次替代起止时间。记录 CSV 请在“我的”导入。"
            }
            val result = mutableListOf<Course>(); val warnings = mutableListOf<String>(); var skipped = 0
            require(rows.size <= 2001) { "课程 CSV 超过 2000 行" }
            rows.drop(1).forEachIndexed { index, row ->
                if (row.all { it.isBlank() }) return@forEachIndexed
                try { require(row.size == rows.first().size) { "列数与表头不一致" }; result += rowCourse(row, headers, semester, periods) }
                catch (e: IllegalArgumentException) { skipped++; if (warnings.size < 30) warnings += "第 ${index + 2} 行：${e.message}" }
                catch (e: IllegalStateException) { skipped++; if (warnings.size < 30) warnings += "第 ${index + 2} 行：${e.message}" }
            }
            require(result.isNotEmpty()) { "未识别到有效课程。${warnings.take(3).joinToString("；")}" }
            return CourseImportPreview(semester.id, "课程 CSV", result.distinctBy { it.importKey() }, warnings, skipped)
        }

        fun parseHtml(html: String, semester: Semester, periods: List<CoursePeriod>): CourseImportPreview {
            require(html.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "页面过大，请打开课表所在的独立页面或导出课程 CSV" }
            val doc = Jsoup.parse(html)
            doc.select("script,style,input,textarea,select,button,iframe").remove()
            val result = mutableListOf<Course>(); val warnings = mutableListOf<String>(); var skipped = 0
            fun attempt(label: String, block: () -> Course) {
                require(result.size < 2000) { "页面课程超过 2000 条" }
                try { result += block() }
                catch (e: IllegalArgumentException) { skipped++; if (warnings.size < 30) warnings += "$label：${e.message}" }
                catch (e: IllegalStateException) { skipped++; if (warnings.size < 30) warnings += "$label：${e.message}" }
            }
            val tables = doc.select("table").filter { it.select("table").size == 1 }
            require(tables.size <= 100) { "页面表格太多，请打开独立课表页面" }
            tables.forEachIndexed { tableIndex, table ->
                val rows = table.select("tr").filter { it.parents().firstOrNull { p -> p.tagName() == "table" } == table }
                val listHeader = rows.indexOfFirst { row ->
                    row.children().mapNotNull { header(it.text()) }.containsAll(listOf("name", "day", "weeks"))
                }
                if (listHeader >= 0) {
                    val headers = rows[listHeader].children().mapIndexedNotNull { index, cell -> header(cell.text())?.let { it to index } }.toMap()
                    rows.drop(listHeader + 1).forEachIndexed { rowIndex, row ->
                        if (row.text().isNotBlank()) attempt("表 ${tableIndex + 1} 第 ${rowIndex + 2} 行") {
                            rowCourse(row.children().map { it.text() }, headers, semester, periods)
                        }
                    }
                    return@forEachIndexed
                }
                // Expand row/column spans so a two-period cell is not interpreted as one period.
                data class Cell(val element: Element, val row: Int, val col: Int, val rowspan: Int, val colspan: Int)
                val grid = mutableMapOf<Pair<Int, Int>, Cell>()
                require(rows.size <= 300) { "表格行数过多" }
                rows.forEachIndexed { r, row ->
                    var col = 0
                    row.children().filter { it.tagName() in listOf("td", "th") }.forEach { el ->
                        while (grid.containsKey(r to col)) col++
                        val rs = (el.attr("rowspan").toIntOrNull() ?: 1).coerceAtLeast(1)
                        val cs = (el.attr("colspan").toIntOrNull() ?: 1).coerceAtLeast(1)
                        require(rs <= 100 && cs <= 30 && col + cs <= 40) { "表格合并单元格过大" }
                        val cell = Cell(el, r, col, rs, cs)
                        for (rr in r until r + rs) for (cc in col until col + cs) grid[rr to cc] = cell
                        col += cs
                    }
                }
                val days = mutableMapOf<Int, Int>(); var headerRow = -1
                rows.indices.forEach { r ->
                    val found = grid.filterKeys { it.first == r }.mapNotNull { (position, cell) ->
                        val text = cell.element.text().trim()
                        if (Regex("^(?:星期|周|礼拜)[一二三四五六日天]$").matches(text)) position.second to day(text) else null
                    }.toMap()
                    if (found.size >= 5 && days.isEmpty()) { days.putAll(found); headerRow = r }
                }
                if (days.isEmpty()) return@forEachIndexed
                val rowPeriods = mutableMapOf<Int, List<Int>>()
                for (r in headerRow + 1 until rows.size) {
                    val labels = grid.filterKeys { it.first == r && it.second < days.keys.min() }.values.distinct()
                    labels.forEach { cell ->
                        val value = cell.element.text().trim()
                        if (Regex("^(?:第)?\\d{1,2}(?:[-－~～]\\d{1,2})?(?:节)?$").matches(value)) {
                            runCatching { Timetable.parseWeeks(value.replace("节", ""), periods.size) }.getOrNull()?.let { rowPeriods[r] = it }
                        }
                    }
                }
                grid.values.distinct().filter { it.row > headerRow && it.col in days && it.element.text().isNotBlank() }.forEach { cell ->
                    val element = cell.element
                    val blocks = element.select(".kbcontent, .timetable_con, [data-course]").filter { candidate ->
                        candidate.parents().takeWhile { it != element }.none { it.hasClass("kbcontent") || it.hasClass("timetable_con") || it.hasAttr("data-course") }
                    }.ifEmpty { listOf(element) }
                    blocks.filter { it.text().trim() !in listOf("-", "无", "午休", "自习", "晚自习") }.forEach { block ->
                        attempt("周${"一二三四五六日"[days.getValue(cell.col) - 1]}课表格") {
                            require(cell.colspan == 1) { "跨星期的合并课程需手动确认" }
                            val clone = block.clone()
                            clone.select("br").forEach { it.after("\n") }
                            clone.select("p,div").forEach { it.appendText("\n") }
                            val lines = clone.wholeText().split('\n').map { it.trim() }.filter { it.isNotBlank() }
                            val text = lines.joinToString("\n")
                            val weekMatches = Regex("(?:第)?(\\d{1,2}(?:\\s*[-－~～至]\\s*\\d{1,2})?(?:\\s*[,，、]\\s*\\d{1,2}(?:\\s*[-－~～至]\\s*\\d{1,2})?)*)\\s*周(?:\\s*[（(]?[单双]周?[）)]?)?").findAll(text).toList()
                            require(weekMatches.size == 1) { "周次缺失或同一格有多段课程，请核对原表" }
                            val weeks = Timetable.parseWeeks(weekMatches.single().value, semester.totalWeeks)
                            val explicit = Regex("(?:第)?(\\d{1,2}(?:[-－~～]\\d{1,2})?)节").find(text)?.value
                            val numbers = (cell.row until cell.row + cell.rowspan).flatMap { rowPeriods[it].orEmpty() }.distinct().sorted()
                            val (start, end) = times(explicit ?: numbers.joinToString(","), periods)
                            fun field(vararg names: String): String = lines.firstNotNullOfOrNull { line ->
                                names.firstNotNullOfOrNull { name -> Regex("^$name[：:]\\s*(.*)$").matchEntire(line)?.groupValues?.get(1) }
                            }.orEmpty()
                            val name = field("课程名称", "课程").ifBlank {
                                block.selectFirst(".title, .course-name")?.text()?.trim().orEmpty()
                            }.ifBlank { lines.firstOrNull().orEmpty() }
                            require(name.isNotBlank() && !name.contains("周次") && !name.contains("节次")) { "课程名称不明确" }
                            Course(name = name, teacher = field("教师", "任课教师", "老师"), location = field("教室", "地点", "上课地点"),
                                dayOfWeek = days.getValue(cell.col), startMinute = start, endMinute = end,
                                semesterId = semester.id, weeks = weeks, note = "网页原文：\n$text").also { it.validate(semester) }
                        }
                    }
                }
            }
            require(result.isNotEmpty()) { "未识别到课程。请打开含周次的完整学期课表；当前页面可能需要专用学校适配。${warnings.take(2).joinToString("；")}" }
            return CourseImportPreview(semester.id, "教务网页 / HTML", result.distinctBy { it.importKey() },
                listOf("请逐项核对周次、节次和地点。只识别有明确周次的列表或周课表；未标注的教师/地点会保留在原文备注中。") + warnings, skipped)
        }
    }
}
