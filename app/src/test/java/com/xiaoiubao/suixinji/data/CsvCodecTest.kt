package com.xiaoiubao.suixinji.data

import org.junit.Assert.*
import org.junit.Test

class CsvCodecTest {
    @Test fun bomQuotedMultilineAndTrailingEmptyColumnSurvive() {
        val rows = CsvCodec.parse("\uFEFFtitle,details,location\r\n\"聚会,周末\",\"第一行\r\n第二行 \"\"引号\"\"\",\r\n")
        assertEquals(listOf("title", "details", "location"), rows[0])
        assertEquals(listOf("聚会,周末", "第一行\r\n第二行 \"引号\"", ""), rows[1])
        assertEquals(2, rows.size)
    }

    @Test fun exportedFieldsRoundTrip() {
        val fields = listOf("普通中文", "逗,号", "双\"引号", "跨\n行", "", "CR\rLF")
        assertEquals(listOf(fields), CsvCodec.parse(fields.joinToString(",") { CsvCodec.escape(it) }))
    }

    @Test fun malformedQuotedInputIsRejected() {
        for (input in listOf("title,details\n\"未闭合", "\"字段\"非法,数据", "ab\"cd")) {
            assertThrows(IllegalArgumentException::class.java) { CsvCodec.parse(input) }
        }
    }
}
