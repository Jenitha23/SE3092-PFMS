package com.pfms.app.data.export

import android.content.ContentResolver
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.OutputStream
import java.io.IOException

enum class IncomeReportFormat(val extension: String) { CSV("csv"), PDF("pdf") }
data class IncomeInsightsReport(val period: String, val rows: List<List<String>>) {
    fun csv(): String = rows.joinToString("\r\n", postfix = "\r\n") { row -> row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } }
}

/** Local document persistence through the location selected by the Android document picker. */
class IncomeInsightsReportWriter(private val resolver: ContentResolver) {
    fun write(uri: Uri, report: IncomeInsightsReport, format: IncomeReportFormat): Result<Unit> = runCatching {
        val output = resolver.openOutputStream(uri, "wt") ?: throw IOException("Document is unavailable")
        output.use { stream ->
            if (format == IncomeReportFormat.CSV) stream.write(report.csv().toByteArray(Charsets.UTF_8))
            else writePdf(stream, report)
        }
    }

    private fun writePdf(output: OutputStream, report: IncomeInsightsReport) {
        val document = PdfDocument()
        try {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = android.graphics.Color.rgb(21, 40, 33) }
            val title = Paint(text).apply { textSize = 17f; isFakeBoldText = true }
            val lines = report.rows.flatMap { cells ->
                val result = mutableListOf<String>()
                var remaining = cells.joinToString("  |  ")
                while (remaining.isNotEmpty()) {
                    val count = text.breakText(remaining, true, 523f, null).coerceAtLeast(1)
                    val wordBreak = remaining.lastIndexOf(' ', count - 1)
                    val end = if (count < remaining.length && wordBreak > 0) wordBreak else count
                    result += remaining.take(end)
                    remaining = remaining.drop(end).trimStart()
                }
                result
            }
            lines.chunked(34).forEachIndexed { index, rows ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                page.canvas.drawText("TrackMyFinances · Income Insights", 36f, 48f, title)
                page.canvas.drawText(report.period, 36f, 72f, text)
                rows.forEachIndexed { rowIndex, line -> page.canvas.drawText(line, 36f, 110f + rowIndex * 19, text) }
                page.canvas.drawText("Recorded income · LKR · Page ${index + 1}", 36f, 810f, text)
                document.finishPage(page)
            }
            document.writeTo(output)
        } finally { document.close() }
    }
}
