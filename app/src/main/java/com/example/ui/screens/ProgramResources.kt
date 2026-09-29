package com.nirogbhumi.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.nirogbhumi.app.data.CloudDocument
import com.nirogbhumi.app.data.CloudResult
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.components.NirogCard
import com.nirogbhumi.app.ui.components.SectionLabel
import com.nirogbhumi.app.ui.components.StatusChip
import com.nirogbhumi.app.ui.components.StatusKind
import com.nirogbhumi.app.ui.theme.NirogColor
import com.nirogbhumi.app.ui.theme.NirogRadius
import com.nirogbhumi.app.ui.theme.NirogSpace
import com.nirogbhumi.app.ui.theme.NirogType

/** One plan / routine / note a coach shared with the batch (Firestore `programResources`). */
data class ProgramResource(
  val id: String,
  val category: String,
  val title: String,
  val body: String,
  val link: String?,
  val weekNumber: Int?,
  val author: String,
  val updatedAtMillis: Long,
)

/** Categories in the order members should see them, with their member-facing labels. */
val ResourceCategories = listOf(
  "diet" to "Diet plan",
  "yoga" to "Yoga",
  "naturopathy" to "Naturopathy",
  "guidance" to "Guidance",
  "other" to "Other",
)

fun resourceCategoryLabel(key: String): String = ResourceCategories.firstOrNull { it.first == key }?.second ?: "Resource"

/** Only ever opens a real web link - anything else a document might carry is ignored. */
fun safeResourceLink(raw: String?): String? {
  val value = raw?.trim().orEmpty()
  return value.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
}

/**
 * Turns raw documents into displayable resources, skipping anything without a
 * title (a half-written document must never render as a blank card) and ordering
 * them Week 1 first, un-weeked notes last, newest first within a week.
 */
fun parseProgramResources(docs: List<CloudDocument>): List<ProgramResource> = docs.mapNotNull { doc ->
  val title = (doc.values["title"] as? String)?.trim().orEmpty()
  if (title.isEmpty()) return@mapNotNull null
  val stamp = (doc.values["updatedAt"] as? Timestamp) ?: (doc.values["createdAt"] as? Timestamp)
  ProgramResource(
    id = doc.id,
    category = (doc.values["category"] as? String) ?: "other",
    title = title,
    body = (doc.values["body"] as? String)?.trim().orEmpty(),
    link = safeResourceLink(doc.values["link"] as? String),
    weekNumber = (doc.values["weekNumber"] as? Number)?.toInt(),
    author = (doc.values["createdByName"] as? String)?.takeIf { it.isNotBlank() } ?: "Your coach",
    updatedAtMillis = stamp?.toDate()?.time ?: 0L,
  )
}.sortedWith(compareBy<ProgramResource> { it.weekNumber ?: Int.MAX_VALUE }.thenByDescending { it.updatedAtMillis })

private fun relativeDay(millis: Long, now: Long = System.currentTimeMillis()): String {
  if (millis <= 0L) return ""
  val days = ((now - millis) / 86_400_000L).toInt()
  return when {
    days <= 0 -> "today"
    days == 1 -> "yesterday"
    days < 7 -> "$days days ago"
    else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(millis))
  }
}

/** Care+ > Plans & guidance: what the member's coach has shared with their batch. */
@Composable
fun ProgramResourcesScreen(state: NirogState) {
  var resources by remember { mutableStateOf<List<ProgramResource>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  val context = LocalContext.current

  DisposableEffect(state.activeProgramId) {
    if (state.activeProgramId.isBlank()) {
      loading = false
      return@DisposableEffect onDispose {}
    }
    val sub = state.repository.listenProgramResources(state.activeProgramId) { result ->
      loading = false
      when (result) {
        is CloudResult.Success -> { resources = parseProgramResources(result.value); error = null }
        is CloudResult.Failure -> error = "We couldn't load your plans just now. Check your connection and try again."
      }
    }
    onDispose { sub.cancel() }
  }

  ProgramResourcesContent(
    resources = resources,
    loading = loading,
    error = error,
    hasProgram = state.activeProgramId.isNotBlank(),
    onBack = { state.currentScreen = "dashboard" },
    onOpenLink = { url ->
      runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { state.cloudMessage = "Couldn't open that link on this device." }
    },
  )
}

@Composable
fun ProgramResourcesContent(
  resources: List<ProgramResource>,
  loading: Boolean,
  error: String?,
  hasProgram: Boolean,
  onBack: () -> Unit,
  onOpenLink: (String) -> Unit,
) {
  var filter by remember { mutableStateOf("all") }
  val present = ResourceCategories.filter { (key, _) -> resources.any { it.category == key } }
  // If the category being viewed disappears (coach deleted the last one), fall back to All.
  val activeFilter = if (filter == "all" || present.any { it.first == filter }) filter else "all"
  val shown = resources.filter { activeFilter == "all" || it.category == activeFilter }

  Column(Modifier.fillMaxSize().background(NirogColor.surface)) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = NirogSpace.sm, vertical = NirogSpace.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NirogColor.forest)
      }
      Column {
        Text("Plans & guidance", style = NirogType.sectionHeading, color = NirogColor.forest)
        Text("Shared by your coach with your batch", style = NirogType.caption, color = NirogColor.inkMuted)
      }
    }

    Column(
      Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = NirogSpace.lg),
      verticalArrangement = Arrangement.spacedBy(NirogSpace.md),
    ) {
      if (present.size > 1) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(NirogSpace.sm)) {
          FilterPill("All", activeFilter == "all") { filter = "all" }
          present.forEach { (key, label) -> FilterPill(label, activeFilter == key) { filter = key } }
        }
      }

      when {
        !hasProgram -> EmptyNote("Join a program to see plans", "Plans and routines from your coach appear here once you're in a batch.")
        loading && resources.isEmpty() -> EmptyNote("Loading your plans…", "")
        error != null && resources.isEmpty() -> EmptyNote("Couldn't load your plans", error)
        resources.isEmpty() -> EmptyNote("Nothing shared yet", "When your coach shares a diet plan, yoga routine or note, it will show up here - and you'll get a notification.")
        else -> shown.forEach { ResourceCard(it, onOpenLink) }
      }
      Text(
        "This is general guidance from your program team. It doesn't replace your doctor's advice - check with them before changing medicines or your routine.",
        style = NirogType.caption, color = NirogColor.inkMuted, modifier = Modifier.padding(vertical = NirogSpace.lg),
      )
    }
  }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
  Box(
    Modifier
      .background(if (selected) NirogColor.forest else NirogColor.surfaceSunken, NirogRadius.pillShape)
      .clickable(onClick = onClick)
      .padding(horizontal = NirogSpace.lg, vertical = NirogSpace.sm),
  ) {
    Text(label, style = NirogType.caption, color = if (selected) androidx.compose.ui.graphics.Color.White else NirogColor.inkSecondary)
  }
}

@Composable
private fun EmptyNote(title: String, message: String) {
  NirogCard {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(title, style = NirogType.sectionHeading, color = NirogColor.forest)
      if (message.isNotBlank()) {
        Spacer(Modifier.size(NirogSpace.sm))
        Text(message, style = NirogType.body, color = NirogColor.inkSecondary)
      }
    }
  }
}

@Composable
private fun ResourceCard(resource: ProgramResource, onOpenLink: (String) -> Unit) {
  var expanded by remember(resource.id) { mutableStateOf(false) }
  // Long text is clamped until asked for: a full week's plan shouldn't push every other card off screen.
  val isLong = resource.body.length > 220 || resource.body.count { it == '\n' } > 5
  NirogCard {
    Column(verticalArrangement = Arrangement.spacedBy(NirogSpace.sm)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NirogSpace.sm)) {
        SectionLabel(resourceCategoryLabel(resource.category))
        resource.weekNumber?.let { StatusChip("Week $it", StatusKind.Neutral) }
      }
      Text(resource.title, style = NirogType.sectionHeading, color = NirogColor.inkPrimary)
      if (resource.body.isNotEmpty()) {
        Text(
          resource.body,
          style = NirogType.body,
          color = NirogColor.inkSecondary,
          maxLines = if (expanded || !isLong) Int.MAX_VALUE else 6,
          overflow = TextOverflow.Ellipsis,
        )
        if (isLong) {
          Text(
            if (expanded) "Show less" else "Read more",
            style = NirogType.caption, color = NirogColor.forestSoft,
            modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = NirogSpace.xs),
          )
        }
      }
      resource.link?.let { url ->
        TextButton(onClick = { onOpenLink(url) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
          Text("Open link", color = NirogColor.forest)
        }
      }
      val meta = listOf(resource.author, relativeDay(resource.updatedAtMillis)).filter { it.isNotBlank() }.joinToString(" · ")
      Text(meta, style = NirogType.caption, color = NirogColor.inkMuted)
    }
  }
}
