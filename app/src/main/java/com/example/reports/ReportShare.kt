package com.nirogbhumi.app.reports

import android.content.Context
import android.content.Intent
import com.nirogbhumi.app.health.domain.HealthSummaryBuilder
import com.nirogbhumi.app.ui.NirogState

object ReportShare {
    /** The weekly report is the same summary as the Health File's weekly view, laid out by the same PDF writer. */
    fun shareWeeklyReport(context: Context, state: NirogState) {
        val health = state.health.ui.value
        val now = System.currentTimeMillis()
        val goals = state.selectedGoals.joinToString { it.label }
        val uri = HealthPdf.write(
            context = context,
            profile = PdfProfile(
                name = state.profileName,
                details = "",
                conditions = listOfNotNull(
                    goals.takeIf { it.isNotBlank() }?.let { "Goals: $it" },
                    "Program: ${if (state.isProgramActive) "Active" else "Not active"}",
                ).joinToString(" | "),
            ),
            summaries = listOf(HealthSummaryBuilder.build(health, 7, now)),
            labTitles = health.labReports.take(10).map { it.title },
            fileName = "nirog-bhumi-weekly-report.pdf",
            documentTitle = "Nirog Bhumi — Weekly health report",
        ).getOrThrow()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Nirog Bhumi weekly report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share report securely"))
    }
}
