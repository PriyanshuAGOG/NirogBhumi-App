package com.nirogbhumi.app.reports

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
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
        listOf(
            "Profile: ${state.profileName}",
            "Latest fasting sugar: ${state.fastingSugarValue} mg/dL",
            "Sleep: ${state.sleepHours}h ${state.sleepMinutes}m",
            "Walking: ${state.stepsLogged} steps",
            "Water: ${state.waterGlasses} glasses",
            "Goals: ${state.selectedGoals.joinToString()}",
            "Program: ${if (state.isProgramActive) "Active" else "Not active"}"
        ).forEachIndexed { index, line -> canvas.drawText(line, 48f, 150f + index * 34f, body) }
        canvas.drawText("Generated from user-entered and connected data.", 48f, 430f, muted)
        canvas.drawText("Education and lifestyle support only; not a diagnosis or treatment plan.", 48f, 450f, muted)
        canvas.drawText("Share only with people you trust.", 48f, 470f, muted)
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
