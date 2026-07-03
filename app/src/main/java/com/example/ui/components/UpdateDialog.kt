package com.nirogbhumi.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nirogbhumi.app.ui.theme.NirogColor
import com.nirogbhumi.app.ui.theme.NirogSpace
import com.nirogbhumi.app.ui.theme.NirogType
import com.nirogbhumi.app.update.DownloadState
import com.nirogbhumi.app.update.UpdateInfo

/**
 * The single "New Update Available" surface - covers the whole lifecycle
 * (prompt -> downloading -> verifying -> ready to install -> failed) so the
 * caller never has to juggle multiple dialogs for one update.
 *
 * `mandatory` updates omit the dismiss button entirely rather than disabling
 * it, matching the "Later only appears when the update isn't mandatory"
 * requirement.
 */
@Composable
fun UpdateDialog(
  info: UpdateInfo,
  downloadState: DownloadState,
  mandatory: Boolean,
  onUpdateNow: () -> Unit,
  onInstall: () -> Unit,
  onRetry: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = { if (!mandatory) onDismiss() },
    icon = {
      val (icon, tint) = when (downloadState) {
        is DownloadState.Failed -> Icons.Filled.Error to NirogColor.statusCritical
        is DownloadState.ReadyToInstall -> Icons.Filled.CheckCircle to NirogColor.statusInRange
        else -> Icons.Filled.SystemUpdate to NirogColor.forest
      }
      Icon(icon, contentDescription = null, tint = tint)
    },
    title = {
      Text(
        when (downloadState) {
          is DownloadState.Failed -> "Update failed"
          is DownloadState.ReadyToInstall -> "Ready to install"
          is DownloadState.InProgress, is DownloadState.Verifying -> "Downloading update"
          DownloadState.Idle -> "New Update Available"
        },
        style = NirogType.cardTitle,
        color = NirogColor.inkPrimary,
      )
    },
    text = {
      Column {
        when (downloadState) {
          DownloadState.Idle -> {
            Text(
              "Version ${info.latestVersionName} is available.",
              style = NirogType.body,
              color = NirogColor.inkSecondary,
            )
            if (info.releaseNotes.isNotBlank()) {
              Spacer(Modifier.height(NirogSpace.md))
              Text("What's new", style = NirogType.bodyStrong, color = NirogColor.inkPrimary)
              Spacer(Modifier.height(NirogSpace.xs))
              info.releaseNotes.lines().filter { it.isNotBlank() }.forEach { line ->
                Text(
                  "• ${line.trim().removePrefix("-").removePrefix("•").trim()}",
                  style = NirogType.body,
                  color = NirogColor.inkSecondary,
                )
              }
            }
            if (mandatory) {
              Spacer(Modifier.height(NirogSpace.md))
              Text(
                "This update is required to keep using Nirog Bhumi.",
                style = NirogType.secondary,
                color = NirogColor.statusAttention,
              )
            }
          }
          is DownloadState.InProgress -> {
            Text(
              "Downloading version ${info.latestVersionName}…",
              style = NirogType.body,
              color = NirogColor.inkSecondary,
            )
            Spacer(Modifier.height(NirogSpace.md))
            LinearProgressIndicator(
              progress = { downloadState.progressPercent / 100f },
              modifier = Modifier.fillMaxWidth().height(6.dp),
              color = NirogColor.forest,
              trackColor = NirogColor.surfaceSunken,
            )
            Spacer(Modifier.height(NirogSpace.xs))
            Text("${downloadState.progressPercent}%", style = NirogType.caption, color = NirogColor.inkMuted)
          }
          is DownloadState.Verifying -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NirogColor.forest)
              Spacer(Modifier.width(NirogSpace.sm))
              Text("Verifying update integrity…", style = NirogType.body, color = NirogColor.inkSecondary)
            }
          }
          is DownloadState.ReadyToInstall -> {
            Text(
              "Version ${info.latestVersionName} has downloaded and been verified. Installing keeps all your data — sign-in, logs, and settings are untouched.",
              style = NirogType.body,
              color = NirogColor.inkSecondary,
            )
          }
          is DownloadState.Failed -> {
            Text(downloadState.message, style = NirogType.body, color = NirogColor.statusCritical)
          }
        }
      }
    },
    confirmButton = {
      when (downloadState) {
        DownloadState.Idle -> TextButton(onClick = onUpdateNow) { Text("Update Now", style = NirogType.button, color = NirogColor.forest) }
        is DownloadState.InProgress, is DownloadState.Verifying -> {}
        is DownloadState.ReadyToInstall -> TextButton(onClick = onInstall) { Text("Install", style = NirogType.button, color = NirogColor.forest) }
        is DownloadState.Failed -> TextButton(onClick = onRetry) { Text("Retry", style = NirogType.button, color = NirogColor.forest) }
      }
    },
    dismissButton = {
      if (!mandatory && downloadState !is DownloadState.InProgress && downloadState !is DownloadState.Verifying) {
        TextButton(onClick = onDismiss) { Text("Later", style = NirogType.button, color = NirogColor.inkMuted) }
      }
    },
  )
}
