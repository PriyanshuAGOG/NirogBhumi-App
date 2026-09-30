package com.nirogbhumi.app.reports

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.nirogbhumi.app.health.domain.DoctorReport
import com.nirogbhumi.app.ui.NirogState
import java.io.File
import java.io.FileOutputStream

object ReportShare {
    fun shareWeeklyReport(context: Context, state: NirogState) {
        val directory = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(directory, "nirog-bhumi-weekly-report.pdf")
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas
        val title = Paint().apply { color = Color.rgb(49, 73, 54); textSize = 26f; isFakeBoldText = true }
        val body = Paint().apply { color = Color.rgb(24, 34, 25); textSize = 14f }
        val muted = Paint().apply { color = Color.rgb(90, 98, 91); textSize = 11f }
        canvas.drawColor(Color.rgb(248, 246, 239))
        canvas.drawText("Nirog Bhumi", 48f, 64f, title)
        canvas.drawText("Weekly health rhythm", 48f, 102f, Paint(title).apply { textSize = 20f })
        val health = state.health.ui.value
        val week = DoctorReport.build(health, 7)
        var y = 150f
        listOfNotNull(
            "Profile: ${state.profileName}",
            "Days logged: ${week.loggedDays} of 7",
            week.sugarSummary?.let { "Blood sugar: $it" } ?: "Blood sugar: not logged this week",
            week.bpSummary?.let { "Blood pressure: $it" },
            week.weightSummary?.let { "Weight: $it" },
            week.sleepSummary?.let { "Sleep: $it" },
            health.week.steps.takeIf { it > 0 }?.let { "Walking: $it steps this week" },
            "Goals: ${state.selectedGoals.joinToString()}",
            "Program: ${if (state.isProgramActive) "Active" else "Not active"}"
        ).forEach { line ->
            // wrap to the page width so long summaries are never cut off
            var rest = line
            while (rest.isNotEmpty()) {
                val fit = body.breakText(rest, true, 499f, null).coerceAtLeast(1)
                val cut = if (fit < rest.length) rest.lastIndexOf(' ', fit - 1).takeIf { it > 0 } ?: fit else fit
                canvas.drawText(rest.substring(0, cut).trim(), 48f, y, body)
                rest = rest.substring(cut).trim()
                y += 22f
            }
            y += 12f
        }
        y += 20f
        canvas.drawText("Generated from user-entered and connected data.", 48f, y, muted)
        canvas.drawText("Education and lifestyle support only; not a diagnosis or treatment plan.", 48f, y + 20f, muted)
        canvas.drawText("Share only with people you trust.", 48f, y + 40f, muted)
        document.finishPage(page)
        FileOutputStream(file).use(document::writeTo)
        document.close()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Nirog Bhumi weekly report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share report securely"))
    }
}
