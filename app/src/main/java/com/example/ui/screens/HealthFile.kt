package com.nirogbhumi.app.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.FileProvider
import com.google.firebase.Timestamp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.nirogbhumi.app.data.CloudResult
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.components.NirogCard
import com.nirogbhumi.app.ui.components.PrimaryButton
import com.nirogbhumi.app.ui.components.SectionLabel
import com.nirogbhumi.app.ui.theme.NirogColor
import com.nirogbhumi.app.ui.theme.NirogSpace
import com.nirogbhumi.app.ui.theme.NirogType
import java.io.File
import java.io.FileOutputStream
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
  var recentSugar by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
  var recentBp by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
  var recentWeight by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
  var labReports by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
  var generating by remember { mutableStateOf(false) }
  var shareError by remember { mutableStateOf<String?>(null) }
  var generatingLink by remember { mutableStateOf(false) }
  var shareLink by remember { mutableStateOf<String?>(null) }
  var linkExpiresInDays by remember { mutableStateOf<Int?>(null) }
  var linkError by remember { mutableStateOf<String?>(null) }

  DisposableEffect(Unit) {
    val subs = listOf(
      state.repository.listenUserCollection("glucoseReadings", 30, orderByField = "measuredAt", descending = true) { r -> if (r is CloudResult.Success) recentSugar = r.value.map { it.values } },
      state.repository.listenUserCollection("bpReadings", 10, orderByField = "createdAt", descending = true) { r -> if (r is CloudResult.Success) recentBp = r.value.map { it.values } },
      state.repository.listenUserCollection("weightLogs", 5, orderByField = "createdAt", descending = true) { r -> if (r is CloudResult.Success) recentWeight = r.value.map { it.values } },
      state.repository.listenUserCollection("labReports", 10, orderByField = "createdAt", descending = true) { r -> if (r is CloudResult.Success) labReports = r.value.map { it.values } },
    )
    onDispose { subs.forEach { it.cancel() } }
  }

  val sugarValues = recentSugar.mapNotNull { (it["value"] as? Number)?.toDouble() }.takeIf { it.isNotEmpty() }
  val sugarAvg30d = sugarValues?.let { it.sum() / it.size }
  val latestBp = recentBp.firstOrNull()
  val latestWeight = recentWeight.firstOrNull()

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
          "Diabetes: ${state.selectedDiabetesStatus} · BP: ${state.selectedBpStatus} · On medication: ${state.selectedOnMedication}",
          style = NirogType.body,
          color = NirogColor.inkSecondary,
        )
      }

      Spacer(Modifier.size(NirogSpace.lg))
      NirogCard {
        SectionLabel("Vitals summary (last 30 days)")
        Spacer(Modifier.size(NirogSpace.sm))
        VitalRow("Fasting sugar (avg)", sugarAvg30d?.let { "${it.toInt()} mg/dL over ${sugarValues!!.size} readings" } ?: "Not logged yet")
        VitalRow("Latest blood pressure", latestBp?.let { "${it["systolic"]}/${it["diastolic"]} mmHg" } ?: "Not logged yet")
        VitalRow("Latest weight", latestWeight?.let { "${(it["valueKg"] as? Number)?.toString() ?: "-"} kg" } ?: "Not logged yet")
      }

      Spacer(Modifier.size(NirogSpace.lg))
      NirogCard {
        SectionLabel("Lab reports")
        Spacer(Modifier.size(NirogSpace.sm))
        if (labReports.isEmpty()) {
          Text("No lab reports uploaded yet.", style = NirogType.body, color = NirogColor.inkMuted)
        } else {
          labReports.forEach { report ->
            val type = report["reportType"] as? String ?: "Lab report"
            val ts = (report["measuredAt"] as? Timestamp) ?: (report["createdAt"] as? Timestamp)
            val date = ts?.toDate()?.let { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(it) } ?: ""
            VitalRow(type, date)
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

      fun generatePdf() = buildAndSaveHealthFilePdf(
        context = context,
        name = state.profileName.ifBlank { "Member" },
        details = listOfNotNull(
          state.profileAge.takeIf { it.isNotBlank() }?.let { "Age: $it" },
          state.profileGender.takeIf { it.isNotBlank() },
          state.profileCity.takeIf { it.isNotBlank() },
        ).joinToString(" | "),
        conditions = "Diabetes: ${state.selectedDiabetesStatus} | BP: ${state.selectedBpStatus} | On medication: ${state.selectedOnMedication}",
        sugarLine = sugarAvg30d?.let { "Fasting sugar average (30d): ${it.toInt()} mg/dL across ${sugarValues!!.size} readings" } ?: "Fasting sugar: not logged yet",
        bpLine = latestBp?.let { "Latest blood pressure: ${it["systolic"]}/${it["diastolic"]} mmHg" } ?: "Blood pressure: not logged yet",
        weightLine = latestWeight?.let { "Latest weight: ${(it["valueKg"] as? Number)}kg" } ?: "Weight: not logged yet",
        labLines = labReports.map { (it["reportType"] as? String ?: "Lab report") },
      )

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
                    val downloadUrl = result.value
                    val storagePath = storagePathFromDownloadUrl(downloadUrl)
                    if (storagePath == null) {
                      generatingLink = false
                      shareLink = downloadUrl
                      linkExpiresInDays = null
                    } else {
                      // Try for a real, time-limited signed URL first; if the
                      // one-time Cloud Functions IAM setup hasn't been done
                      // yet (see getHealthFileShareLink), fall back to the
                      // Storage download URL rather than leaving the member
                      // with no link at all.
                      state.repository.getHealthFileShareLink(storagePath) { signedResult ->
                        generatingLink = false
                        when (signedResult) {
                          is CloudResult.Success -> { shareLink = signedResult.value; linkExpiresInDays = 7 }
                          is CloudResult.Failure -> { shareLink = downloadUrl; linkExpiresInDays = null }
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
private fun VitalRow(label: String, value: String) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = 6.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = NirogType.body, color = NirogColor.inkSecondary)
    Text(value, style = NirogType.bodyStrong, color = NirogColor.inkPrimary)
  }
}

/** Builds a simple, real PDF (no external libs) from the member's own data and returns a shareable content Uri. */
private fun buildAndSaveHealthFilePdf(
  context: android.content.Context,
  name: String,
  details: String,
  conditions: String,
  sugarLine: String,
  bpLine: String,
  weightLine: String,
  labLines: List<String>,
): Result<android.net.Uri> {
  return runCatching {
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 at 72dpi
    val page = document.startPage(pageInfo)
    val canvas = page.canvas
    val titlePaint = Paint().apply { textSize = 22f; isFakeBoldText = true; color = 0xFF1B3221.toInt() }
    val headingPaint = Paint().apply { textSize = 13f; isFakeBoldText = true; color = 0xFF1B3221.toInt() }
    val bodyPaint = Paint().apply { textSize = 12f; color = 0xFF1B2219.toInt() }
    val mutedPaint = Paint().apply { textSize = 10f; color = 0xFF8B9285.toInt() }

    var y = 50f
    canvas.drawText("Nirog Bhumi — Health File", 40f, y, titlePaint); y += 20f
    canvas.drawText("Generated ${SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date())}", 40f, y, mutedPaint); y += 30f

    canvas.drawText(name, 40f, y, headingPaint); y += 16f
    canvas.drawText(details, 40f, y, bodyPaint); y += 16f
    canvas.drawText(conditions, 40f, y, bodyPaint); y += 30f

    canvas.drawText("Vitals summary", 40f, y, headingPaint); y += 18f
    canvas.drawText(sugarLine, 40f, y, bodyPaint); y += 16f
    canvas.drawText(bpLine, 40f, y, bodyPaint); y += 16f
    canvas.drawText(weightLine, 40f, y, bodyPaint); y += 30f

    canvas.drawText("Lab reports", 40f, y, headingPaint); y += 18f
    if (labLines.isEmpty()) {
      canvas.drawText("No lab reports uploaded yet.", 40f, y, bodyPaint); y += 16f
    } else {
      labLines.forEach { canvas.drawText("- $it", 40f, y, bodyPaint); y += 16f }
    }
    y += 20f
    canvas.drawText("Prepared with Nirog Bhumi — data logged by the patient.", 40f, y, mutedPaint)

    document.finishPage(page)

    val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
    val file = File(reportsDir, "HealthFile_${System.currentTimeMillis()}.pdf")
    FileOutputStream(file).use { document.writeTo(it) }
    document.close()

    FileProvider.getUriForFile(context, "${context.packageName}.files", file)
  }
}
