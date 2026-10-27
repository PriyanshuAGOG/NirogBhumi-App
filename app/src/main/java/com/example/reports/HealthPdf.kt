package com.nirogbhumi.app.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.nirogbhumi.app.health.domain.ChangeTone
import com.nirogbhumi.app.health.domain.HealthSummary
import com.nirogbhumi.app.health.domain.SummaryMetric
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfProfile(val name: String, val details: String, val conditions: String)

/**
 * The one PDF writer for the Health File and the weekly report: wrapped text (nothing runs off the page), automatic new pages,
 * a footer with page numbers, and a small trend chart beside each metric. Built from [HealthSummary], so the printout always
 * says what the screen says.
 */
object HealthPdf {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val SPARK_W = 120f
    private const val SPARK_H = 38f

    private val ink = 0xFF1B2219.toInt()
    private val forest = 0xFF1B3221.toInt()
    private val muted = 0xFF6B7468.toInt()
    private val rule = 0xFFD9DED5.toInt()
    private val better = 0xFF2E7D32.toInt()
    private val worse = 0xFFB3261E.toInt()

    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; isFakeBoldText = true; color = forest }
    private val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; isFakeBoldText = true; color = forest }
    private val metricTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12.5f; isFakeBoldText = true; color = ink }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = ink }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = muted }

    fun write(context: Context, profile: PdfProfile, summaries: List<HealthSummary>, labTitles: List<String>, fileName: String, heading: String = "Nirog Bhumi — Health File"): Result<Uri> = runCatching {
        val document = PdfDocument()
        val flow = Flow(document)
        flow.newPage()

        flow.text(heading, title, lineHeight = 26f)
        flow.text("Prepared ${SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date())}", small, lineHeight = 18f)
        flow.gap(8f)
        flow.text(profile.name.ifBlank { "Member" }, heading, lineHeight = 18f)
        if (profile.details.isNotBlank()) flow.text(profile.details, body)
        if (profile.conditions.isNotBlank()) flow.text(profile.conditions, body)
        flow.gap(10f)

        summaries.forEach { summary ->
            flow.need(120f)
            flow.rule()
            flow.text("Last ${summary.periodDays} days — ${summary.loggedDays} of ${summary.periodDays} days logged", heading, lineHeight = 20f)
            flow.gap(4f)
            summary.metrics.forEach { metric(flow, it) }
            flow.gap(8f)
        }

        flow.need(80f)
        flow.rule()
        flow.text("Lab reports", heading, lineHeight = 20f)
        if (labTitles.isEmpty()) flow.text("No lab reports uploaded yet.", body) else labTitles.forEach { flow.text("• $it", body) }
        flow.gap(12f)
        flow.text("Prepared with Nirog Bhumi from data the patient logged or approved from a connected app. Education and lifestyle support only; not a diagnosis or treatment plan.", small)
        flow.finish()

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun metric(flow: Flow, m: SummaryMetric) {
        // A metric block is kept together on one page.
        val textWidth = flow.contentWidth - SPARK_W - 12f
        val blockHeight = 70f + m.lines.size * 26f   // generous: wrapped lines may take two rows each
        flow.need(maxOf(blockHeight, SPARK_H + 24f))
        val top = flow.y
        flow.text(m.title, metricTitle, width = textWidth, lineHeight = 16f)
        flow.text(m.headline, body, width = textWidth, lineHeight = 15f)
        if (m.change.text.isNotBlank()) {
            val paint = Paint(body).apply { color = when (m.change.tone) { ChangeTone.BETTER -> better; ChangeTone.WORSE -> worse; else -> muted } }
            flow.text(m.change.text, paint, width = textWidth, lineHeight = 14f)
        }
        m.lines.forEach { flow.text("${it.label}: ${it.value}", small, indent = 8f, width = textWidth - 8f, lineHeight = 13f) }
        val bottom = flow.y
        if (m.spark.size >= 2) flow.spark(m.spark, flow.left + flow.contentWidth - SPARK_W, top + 2f, SPARK_W, SPARK_H, colorFor(m))
        flow.y = maxOf(bottom, top + SPARK_H + 10f) + 8f
    }

    private fun colorFor(m: SummaryMetric): Int = when (m.change.tone) { ChangeTone.BETTER -> better; ChangeTone.WORSE -> worse; else -> forest }

    /** Lays out text top to bottom, starting a new page when the next block will not fit. */
    private class Flow(private val document: PdfDocument) {
        val left = MARGIN
        val contentWidth = PAGE_W - 2 * MARGIN
        var y = MARGIN
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var pageNumber = 0

        fun newPage() {
            finishPage()
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            canvas = page!!.canvas
            y = MARGIN + 10f
        }

        private fun finishPage() {
            val p = page ?: return
            canvas!!.drawText("Page $pageNumber", PAGE_W - MARGIN - 40f, PAGE_H - 24f, small)
            document.finishPage(p)
            page = null
        }

        fun finish() = finishPage()

        fun need(height: Float) { if (y + height > PAGE_H - 50f) newPage() }
        fun gap(h: Float) { y += h }

        fun rule() {
            canvas!!.drawLine(left, y, left + contentWidth, y, Paint().apply { color = rule; strokeWidth = 1f })
            y += 14f
        }

        /** Draws [text] wrapped to [width], word by word. */
        fun text(text: String, paint: Paint, indent: Float = 0f, width: Float = contentWidth, lineHeight: Float = 15f) {
            val maxWidth = width - indent
            var line = StringBuilder()
            fun flush() {
                if (line.isEmpty()) return
                need(lineHeight)
                y += lineHeight
                canvas!!.drawText(line.toString(), left + indent, y - 3f, paint)
                line = StringBuilder()
            }
            for (word in text.split(' ')) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) <= maxWidth) { line = StringBuilder(candidate); continue }
                flush()
                // A single word longer than the line is cut rather than allowed to run off the page.
                var rest = word
                while (paint.measureText(rest) > maxWidth && rest.length > 1) {
                    val fit = paint.breakText(rest, true, maxWidth, null).coerceAtLeast(1)
                    line = StringBuilder(rest.substring(0, fit)); flush(); rest = rest.substring(fit)
                }
                line = StringBuilder(rest)
            }
            flush()
        }

        /** A small line chart with a dot on the latest value. */
        fun spark(values: List<Double>, x: Float, top: Float, w: Float, h: Float, color: Int) {
            val c = canvas ?: return
            val min = values.min(); val max = values.max()
            val span = (max - min).takeIf { it > 1e-9 } ?: 1.0
            fun px(i: Int) = x + w * i / (values.size - 1)
            fun py(v: Double) = top + h - (h * ((v - min) / span)).toFloat()
            c.drawLine(x, top + h, x + w, top + h, Paint().apply { this.color = rule; strokeWidth = 0.8f })
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(px(i), py(v)) else path.lineTo(px(i), py(v)) }
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.6f })
            c.drawCircle(px(values.size - 1), py(values.last()), 2.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        }
    }
}
