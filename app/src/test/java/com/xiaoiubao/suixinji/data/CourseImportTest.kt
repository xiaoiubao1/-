package com.xiaoiubao.suixinji.data

import org.junit.Assert.*
import org.junit.Test

class CourseImportTest {
    private val semester = Semester(1, "秋季", "2026-09-07", 20)
    private val periods = Timetable.defaultPeriods()
    @Test fun csvSupportsBomMultilineFieldsAndReportsBadRows() {
        val csv = "\uFEFF课程名称,教师,教室,星期,开始时间,结束时间,周次,备注\r\n" +
            "数学,张老师,A101,周一,08:30,10:10,1-16(单),\"第一行\n第二行\"\r\n" +
            "错误课程,,,周二,25:00,26:00,1-20,\r\n"
        val preview = CourseImportService.parseCsv(csv, semester, periods)
        assertEquals(1, preview.skippedRows)
        assertFalse(preview.allowReplace)
        assertEquals(1, preview.courses.size)
        assertEquals("第一行\n第二行", preview.courses.single().note)
        assertEquals((1..16).filter { it % 2 == 1 }, preview.courses.single().weeks)
        assertFalse(preview.courses.single().reminderEnabled)
    }
    @Test fun onlyCompleteCsvCanOfferReplacement() {
        val preview = CourseImportService.parseCsv("name,day,periods,weeks\n数学,1,1-2,1-20", semester, periods)
        assertTrue(preview.allowReplace)
        assertEquals(0, preview.skippedRows)
    }
    @Test fun notesCsvAndMissingWeeksNeverBecomeCourseImports() {
        assertTrue(runCatching { CourseImportService.parseCsv("title,details\n记录,内容", semester, periods) }.isFailure)
        assertTrue(runCatching { CourseImportService.parseCsv("name,day,periods,weeks\n数学,1,1-2,", semester, periods) }.isFailure)
    }
    @Test fun htmlListTableUsesHeaderMeaningsRatherThanColumnOrder() {
        val html = """<table><tr><th>星期</th><th>周次</th><th>课程名称</th><th>节次</th><th>教室</th></tr>
            <tr><td>周三</td><td>2-10周(双)</td><td>物理</td><td>3-4节</td><td>B201</td></tr></table>"""
        val course = CourseImportService.parseHtml(html, semester, periods).courses.single()
        assertEquals(3, course.dayOfWeek)
        assertEquals(listOf(2, 4, 6, 8, 10), course.weeks)
        assertEquals(630, course.startMinute)
        assertEquals(730, course.endMinute)
        assertEquals("B201", course.location)
    }
    @Test fun gridPreservesMergedPeriodsAndMultipleCoursesInOneCell() {
        val html = """<table>
            <tr><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th></tr>
            <tr><td>第1节</td><td rowspan="2"><div class="kbcontent">数学<br>教师：张老师<br>教室：A101<br>1-16周(单)</div><div class="kbcontent">英语<br>教师：李老师<br>教室：A102<br>2-16周(双)</div></td><td></td><td></td><td></td><td></td></tr>
            <tr><td>第2节</td><td></td><td></td><td></td><td></td></tr></table>"""
        val preview = CourseImportService.parseHtml(html, semester, periods)
        assertEquals(0, preview.skippedRows)
        assertFalse("HTML completeness cannot be guaranteed", preview.allowReplace)
        assertEquals(2, preview.courses.size)
        assertEquals(setOf("数学", "英语"), preview.courses.map { it.name }.toSet())
        assertTrue(preview.courses.all { it.startMinute == 510 && it.endMinute == 610 })
        assertEquals("张老师", preview.courses.first().teacher)
    }
    @Test fun ambiguousGridWithoutTeachingWeeksFailsWithoutGuessing() {
        val html = """<table><tr><th>节次</th><th>周一</th><th>周二</th><th>周三</th><th>周四</th><th>周五</th></tr>
            <tr><td>1</td><td>数学<br>教师：张</td><td></td><td></td><td></td><td></td></tr></table>"""
        assertTrue(runCatching { CourseImportService.parseHtml(html, semester, periods) }.isFailure)
    }
}
