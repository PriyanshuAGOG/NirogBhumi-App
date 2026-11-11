package com.nirogbhumi.app.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.nirogbhumi.app.data.CloudResult
import com.nirogbhumi.app.health.domain.ChangeTone
import com.nirogbhumi.app.health.domain.HealthSummary
import com.nirogbhumi.app.health.domain.HealthSummaryBuilder
import com.nirogbhumi.app.health.domain.SummaryMetric
import com.nirogbhumi.app.reports.HealthPdf
import com.nirogbhumi.app.reports.PdfProfile
import com.nirogbhumi.app.ui.collectHealth
import com.nirogbhumi.app.ui.diabetesAnswer
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.components.NirogCard
import com.nirogbhumi.app.ui.components.PrimaryButton
import com.nirogbhumi.app.ui.components.SectionLabel
import com.nirogbhumi.app.ui.theme.NirogColor
import com.nirogbhumi.app.ui.theme.NirogSpace
import com.nirogbhumi.app.ui.theme.NirogType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Health File (PRD v2 flagship USP) - an always-current, doctor-ready summary
 * built from the member's own logged data, with a real "Share" action that
 * generates a PDF and opens the system share sheet (WhatsApp/email/print all
 * work through it, matching how patients already carry paper files in India).
 *
 * Everything shown is read from Firestore; sections with no data yet say so
 * plainly instead of being hidden or faked.
 */
@Composable
fun HealthFileScreen(state: NirogState) {
  val context = LocalContext.current
  val health = state.collectHealth()
  var generating by remember { mutableStateOf(false) }
  var shareError by remember { mutableStateOf<String?>(null) }
  var generatingLink by remember { mutableStateOf(false) }
  var shareLink by remember { mutableStateOf<String?>(null) }
  var linkExpiresInDays by remember { mutableStateOf<Int?>(null) }
  var linkError by remember { mutableStateOf<String?>(null) }
  // One summary for the screen, the PDF and the weekly report. Weekly and monthly are two views of the same thing.
  var reportPeriodDays by remember { mutableStateOf(7) }
  val labReports = health.labReports.take(10)
  val summary = remember(health, reportPeriodDays) { HealthSummaryBuilder.build(health, reportPeriodDays, System.currentTimeMillis()) }

  Column(Modifier.fillMaxSize().background(NirogColor.surface)) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = NirogSpace.sm, vertical = NirogSpace.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = { state.currentScreen = "dashboard" }) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NirogColor.forest)
      }
      Column {
        Text("Health File", style = NirogType.sectionHeading, color = NirogColor.forest)
        Text("Always ready to show any doctor", style = NirogType.caption, color = NirogColor.inkMuted)
      }
    }

    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = NirogSpace.lg),
    ) {
      NirogCard {
        SectionLabel("Profile")
        Spacer(Modifier.size(NirogSpace.sm))
        Text(state.profileName.ifBlank { "Not set" }, style = NirogType.cardTitle, color = NirogColor.inkPrimary)
        Text(
          listOfNotNull(
            state.profileAge.takeIf { it.isNotBlank() }?.let { "$it yrs" },
            state.profileGender.takeIf { it.isNotBlank() },
            state.profileCity.takeIf { it.isNotBlank() },
          ).joinToString(" · "),
          style = NirogType.caption,
          color = NirogColor.inkMuted,
        )
        Spacer(Modifier.size(NirogSpace.sm))
        Text(
          profileSummary(state, " · "),
          style = NirogType.body,
          color = NirogColor.inkSecondary,
        )
      }

      Spacer(Modifier.size(NirogSpace.lg))
      HealthSummaryCard(summary, health.isLoading, reportPeriodDays) { reportPeriodDays = it }

      Spacer(Modifier.size(NirogSpace.lg))
      NirogCard {
        SectionLabel("Lab reports")
        Spacer(Modifier.size(NirogSpace.sm))
        if (labReports.isEmpty()) {
          Text("No lab reports uploaded yet.", style = NirogType.body, color = NirogColor.inkMuted)
        } else {
          labReports.forEach { report ->
            val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(report.measuredAtMillis))
            VitalRow(report.title, date)
          }
        }
      }

      Spacer(Modifier.size(NirogSpace.xl))
      if (shareError != null) {
        Box(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NirogColor.statusCriticalBg)
            .padding(NirogSpace.lg),
        ) {
          Text(shareError!!, style = NirogType.caption, color = NirogColor.statusCritical)
        }
        Spacer(Modifier.size(NirogSpace.md))
      }
      if (linkError != null) {
        Box(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NirogColor.statusCriticalBg)
            .padding(NirogSpace.lg),
        ) {
          Text(linkError!!, style = NirogType.caption, color = NirogColor.statusCritical)
        }
        Spacer(Modifier.size(NirogSpace.md))
      }

      fun generatePdf(): Result<android.net.Uri> {
        val now = System.currentTimeMillis()
        return HealthPdf.write(
          context = context,
          profile = PdfProfile(
            name = state.profileName,
            details = listOfNotNull(
              state.profileAge.takeIf { it.isNotBlank() }?.let { "Age: $it" },
              state.profileGender.takeIf { it.isNotBlank() },
              state.profileCity.takeIf { it.isNotBlank() },
            ).joinToString(" | "),
            conditions = profileSummary(state, " | "),
          ),
          summaries = listOf(7, 30).map { HealthSummaryBuilder.build(health, it, now) },
          labTitles = labReports.map { it.title },
          fileName = "HealthFile_$now.pdf",
        )
      }

      if (generating) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = NirogColor.forest)
        }
      } else {
        PrimaryButton(
          "Share Health File",
          onClick = {
            generating = true
            shareError = null
            val result = generatePdf()
            generating = false
            result.onSuccess { uri ->
              runCatching {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                  type = "application/pdf"
                  putExtra(Intent.EXTRA_STREAM, uri)
                  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Health File"))
              }.onFailure {
                shareError = "No app available to share the PDF. It was saved on your device."
              }
            }.onFailure {
              shareError = "The Health File couldn't be created. Please try again."
            }
          },
        )
      }

      Spacer(Modifier.size(NirogSpace.sm))

      if (generatingLink) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = NirogColor.forestSoft, modifier = Modifier.size(24.dp))
        }
      } else {
        OutlinedButton(
          onClick = {
            generatingLink = true
            linkError = null
            val pdfResult = generatePdf()
            val uri = pdfResult.getOrNull()
            if (uri == null) {
              generatingLink = false
              linkError = "The Health File couldn't be created. Please try again."
            } else {
              state.repository.uploadPrivateFile("health-file", uri) { result ->
                when (result) {
                  is CloudResult.Success -> {
                    val storagePath = storagePathFromDownloadUrl(result.value)
                    if (storagePath == null) {
                      generatingLink = false
                      linkError = SECURE_LINK_UNAVAILABLE
                    } else {
                      // Only ever a real, time-limited signed link. If one
                      // can't be issued we fail closed: falling back to the
                      // raw Storage download URL would hand out a permanent,
                      // unrevocable link to the member's health data.
                      state.repository.getHealthFileShareLink(storagePath) { signedResult ->
                        generatingLink = false
                        when (signedResult) {
                          is CloudResult.Success -> { shareLink = signedResult.value; linkExpiresInDays = 7 }
                          is CloudResult.Failure -> linkError = SECURE_LINK_UNAVAILABLE
                        }
                      }
                    }
                  }
                  is CloudResult.Failure -> { generatingLink = false; linkError = result.message }
                }
              }
            }
          },
          modifier = Modifier.fillMaxWidth(),
        ) { Text("Get shareable link / QR code") }
      }
      Spacer(Modifier.size(NirogSpace.xxl))
    }
  }

  shareLink?.let { link ->
    HealthFileLinkDialog(link, expiresInDays = linkExpiresInDays, onDismiss = { shareLink = null; linkExpiresInDays = null })
  }
}

private const val SECURE_LINK_UNAVAILABLE = "A secure, expiring link isn't available right now. Please use \"Share Health File\" to send the PDF directly instead - it never creates a public link."

/** Recovers the raw Storage path (e.g. "users/uid/health-file/xyz") from a
 * Firebase Storage download URL so it can be passed to getHealthFileShareLink
 * without changing uploadPrivateFile's signature (used by many other callers). */
private fun storagePathFromDownloadUrl(url: String): String? {
  val marker = "/o/"
  val idx = url.indexOf(marker)
  if (idx == -1) return null
  val encodedPath = url.substring(idx + marker.length).substringBefore("?")
  return runCatching { java.net.URLDecoder.decode(encodedPath, "UTF-8") }.getOrNull()
}

@Composable
private fun HealthFileLinkDialog(link: String, expiresInDays: Int?, onDismiss: () -> Unit) {
  val clipboard = LocalClipboardManager.current
  val qrBitmap = remember(link) { runCatching { generateQrBitmap(link) }.getOrNull() }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Shareable Health File link", style = NirogType.cardTitle, color = NirogColor.inkPrimary) },
    text = {
      Column {
        if (qrBitmap != null) {
          Image(
            bitmap = qrBitmap.asImageBitmap(),
            contentDescription = "QR code for Health File link",
            modifier = Modifier.fillMaxWidth().size(220.dp),
          )
          Spacer(Modifier.size(NirogSpace.md))
        }
        Text(link, style = NirogType.caption, color = NirogColor.inkSecondary)
        Spacer(Modifier.size(NirogSpace.sm))
        // Honest either way: a real signed URL expires on its own (no
        // lingering access after the window), while the fallback bearer-
        // token link doesn't expire on its own - never overstate which one
        // the member actually got.
        Text(
          if (expiresInDays != null)
            "Anyone with this link or QR code can view your Health File without signing in - don't post it publicly. This link stops working in $expiresInDays days."
          else
            "Anyone with this link or QR code can view your Health File without signing in - don't post it publicly. It doesn't expire on its own yet.",
          style = NirogType.caption, color = NirogColor.inkMuted,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = { clipboard.setText(AnnotatedString(link)) }) { Text("Copy link", color = NirogColor.forest) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = NirogColor.inkSecondary) } },
  )
}

private fun generateQrBitmap(text: String, sizePx: Int = 512): Bitmap {
  val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
  val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
  for (x in 0 until sizePx) {
    for (y in 0 until sizePx) {
      bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
  }
  return bitmap
}

@Composable
private fun HealthSummaryCard(summary: HealthSummary, loading: Boolean, periodDays: Int, onPeriod: (Int) -> Unit) {
  NirogCard {
    SectionLabel("Health summary")
    Spacer(Modifier.size(NirogSpace.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ReportPeriodChip("Weekly", periodDays == 7) { onPeriod(7) }
      ReportPeriodChip("Monthly", periodDays == 30) { onPeriod(30) }
    }
    Spacer(Modifier.size(NirogSpace.sm))
    Text(
      when {
        loading -> "Loading your readings…"
        summary.loggedDays == 0 -> "Nothing logged in the last $periodDays days yet."
        else -> "${summary.loggedDays} of $periodDays days logged. Arrows compare with the ${periodDays} days before."
      },
      style = NirogType.caption, color = NirogColor.inkMuted,
    )
    summary.metrics.forEach { metric ->
      Spacer(Modifier.size(NirogSpace.md))
      Box(Modifier.fillMaxWidth().height(1.dp).background(NirogColor.surfaceSunken))
      Spacer(Modifier.size(NirogSpace.md))
      SummaryMetricBlock(metric, periodDays)
    }
  }
}

@Composable
private fun SummaryMetricBlock(metric: SummaryMetric, periodDays: Int) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
    Column(Modifier.weight(1f)) {
      Text(metric.title, style = NirogType.caption, color = NirogColor.inkMuted)
      Text(metric.headline, style = NirogType.bodyStrong, color = if (metric.hasData) NirogColor.inkPrimary else NirogColor.inkMuted)
      if (metric.change.text.isNotBlank()) {
        Spacer(Modifier.size(4.dp))
        ChangeLine(metric)
      }
    }
    if (metric.spark.size >= 2) {
      Spacer(Modifier.width(12.dp))
      MiniTrend(metric.spark, toneColor(metric.change.tone), "Trend over the last $periodDays days for ${metric.title}")
    }
  }
  if (metric.lines.isNotEmpty()) {
    Spacer(Modifier.size(4.dp))
    metric.lines.forEach { VitalRow(it.label, it.value) }
  }
}

private val ToneBetter = Color(0xFF2E7D32)

private fun toneColor(tone: ChangeTone): Color = when (tone) {
  ChangeTone.BETTER -> ToneBetter
  ChangeTone.WORSE -> NirogColor.statusCritical
  ChangeTone.NEUTRAL, ChangeTone.NONE -> NirogColor.forest
}

@Composable
private fun ChangeLine(metric: SummaryMetric) {
  val color = when (metric.change.tone) { ChangeTone.BETTER -> ToneBetter; ChangeTone.WORSE -> NirogColor.statusCritical; else -> NirogColor.inkSecondary }
  Row(verticalAlignment = Alignment.CenterVertically) {
    // Direction arrow drawn directly so it never depends on a font having the glyph.
    Canvas(Modifier.size(12.dp)) {
      val w = size.width; val h = size.height
      val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round)
      when (metric.change.direction) {
        1 -> drawPath(Path().apply { moveTo(w / 2, 1f); lineTo(w - 1f, h - 2f); lineTo(1f, h - 2f); close() }, color)
        -1 -> drawPath(Path().apply { moveTo(w / 2, h - 1f); lineTo(w - 1f, 2f); lineTo(1f, 2f); close() }, color)
        else -> drawLine(color, Offset(1f, h / 2), Offset(w - 1f, h / 2), stroke.width, StrokeCap.Round)
      }
    }
    Spacer(Modifier.width(6.dp))
    Text(metric.change.text, style = NirogType.caption, color = color)
  }
}

/** A small line chart of the daily values, with a dot on the latest one. */
@Composable
private fun MiniTrend(values: List<Double>, color: Color, description: String) {
  Canvas(Modifier.width(96.dp).height(36.dp).semantics { contentDescription = description }) {
    val min = values.min(); val max = values.max()
    val span = (max - min).takeIf { it > 1e-9 } ?: 1.0
    fun x(i: Int) = size.width * i / (values.size - 1)
    fun y(v: Double) = size.height - (size.height * ((v - min) / span)).toFloat()
    drawLine(color.copy(alpha = 0.18f), Offset(0f, size.height), Offset(size.width, size.height), 1.5f)
    val path = Path()
    values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
    drawPath(path, color, style = Stroke(width = 3f, cap = StrokeCap.Round))
    drawCircle(color, 4.5f, Offset(x(values.size - 1), y(values.last())))
  }
}

@Composable
private fun VitalRow(label: String, value: String) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = 6.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = NirogType.body, color = NirogColor.inkSecondary)
    Text(value, style = NirogType.bodyStrong, color = NirogColor.inkPrimary)
  }
}

@Composable
private fun ReportPeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
  Box(
    Modifier
      .clip(RoundedCornerShape(50))
      .background(if (selected) NirogColor.forest else NirogColor.surfaceSunken)
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Text(label, style = NirogType.caption, color = if (selected) NirogColor.onAccent else NirogColor.inkSecondary)
  }
}

/** What the member told us about their health, in one line. Questions they skipped are left out rather than reported as "None". */
private fun profileSummary(state: NirogState, separator: String): String {
  if (!state.healthProfileCompleted) return "Health profile not filled in"
  return listOfNotNull(
    state.diabetesAnswer()?.let { "Diabetes: ${it.describe()}" },
    "Blood pressure: ${state.selectedBpStatus}",
    "On medication: ${state.selectedOnMedication}",
  ).joinToString(separator)
}
