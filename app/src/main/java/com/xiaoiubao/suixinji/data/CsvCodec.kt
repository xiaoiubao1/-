package com.xiaoiubao.suixinji.data

/** CSV records may span physical lines; a BOM belongs to the stream, not the first header. */
object CsvCodec {
    fun parse(input: String): List<List<String>> {
        val text = input.removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closedQuote = false
        var touched = false
        var i = 0
        fun finishField() { row.add(field.toString()); field.setLength(0); closedQuote = false }
        fun finishRow() {
            finishField()
            if (touched || row.any { it.isNotBlank() }) rows.add(row.toList())
            row.clear(); touched = false
        }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ }
                    else { quoted = false; closedQuote = true }
                } else field.append(c)
            } else when (c) {
                '"' -> { require(field.isEmpty() && !closedQuote) { "CSV 引号位置错误" }; quoted = true; touched = true }
                ',' -> { finishField(); touched = true }
                '\n', '\r' -> {
                    finishRow()
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> { require(!closedQuote) { "CSV 引号后存在非法字符" }; field.append(c); touched = true }
            }
            i++
        }
        require(!quoted) { "CSV 存在未闭合的引号" }
        if (touched || row.isNotEmpty() || field.isNotEmpty()) finishRow()
        return rows
    }

    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"${value.replace("\"", "\"\"")}\"" else value
}
