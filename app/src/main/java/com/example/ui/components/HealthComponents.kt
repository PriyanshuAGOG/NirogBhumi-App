package com.nirogbhumi.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirogbhumi.app.health.domain.ClockText
import com.nirogbhumi.app.health.domain.EditWindow
import com.nirogbhumi.app.health.domain.Editability
import com.nirogbhumi.app.health.domain.HealthEntry
import com.nirogbhumi.app.health.domain.HealthUiState
import com.nirogbhumi.app.health.domain.TrendBuilder
import com.nirogbhumi.app.health.domain.TrendData
import com.nirogbhumi.app.health.domain.TrendMetric
import com.nirogbhumi.app.health.domain.TrendRange
import com.nirogbhumi.app.health.domain.TrendText
import com.nirogbhumi.app.ui.theme.NirogColor
import java.time.LocalDate
import kotlinx.coroutines.delay
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Trend card for one health metric: 7 / 30 / 90 day chips, a chart with real axes and dates, exact
 * values on tap, and a list version of the same numbers for screen readers. Reads only the shared
 * [HealthUiState], so it always agrees with every other screen.
 */
@Composable
fun MetricTrendCard(health: HealthUiState, metric: TrendMetric, modifier: Modifier = Modifier, title: String = "Trend", initialRange: TrendRange = TrendRange.WEEK) {
    var range by rememberSaveable(metric) { mutableStateOf(initialRange) }
    val data = remember(health, metric, range) { TrendBuilder.build(health, metric, range, System.currentTimeMillis()) }
    Card(
        modifier = modifier.fillMaxWidth().border(0.5.dp, NirogColor.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, color = NirogColor.forest)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrendRange.entries.forEach { r ->
                        val isSelected = range == r
                        Box(
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) NirogColor.forestSoft else NirogColor.surfaceNeutral)
                                .clickable { range = r }
                                .semantics { role = Role.RadioButton; selected = isSelected; contentDescription = r.label }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(r.label.removeSuffix("s").replace(" day", "D"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else NirogColor.inkTertiary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            when {
                health.isLoading -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 24.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = NirogColor.forestSoft)
                    Spacer(Modifier.width(8.dp))
                    Text("Loading your readings…", fontSize = 13.sp, color = NirogColor.inkTertiary)
                }
                !data.hasEnoughData -> Text(
                    data.message ?: TrendBuilder.INSUFFICIENT_MESSAGE,
                    fontSize = 13.sp, color = NirogColor.inkTertiary, modifier = Modifier.padding(vertical = 20.dp),
                )
                else -> MetricTrendChart(data)
            }
        }
    }
}

private val seriesColors = listOf(NirogColor.forest, NirogColor.terracotta)

@Composable
fun MetricTrendChart(data: TrendData, modifier: Modifier = Modifier) {
    val axis = data.axis ?: return
    val measurer = rememberTextMeasurer()
    var selected by remember(data) { mutableStateOf<LocalDate?>(null) }
    var showList by rememberSaveable(data.metric) { mutableStateOf(false) }
    val totalDays = ChronoUnit.DAYS.between(data.firstDate, data.lastDate).toInt() + 1
    val xLabels = remember(data) { TrendBuilder.xLabels(data.range, data.firstDate, data.lastDate) }
    val labelStyle = TextStyle(fontSize = 10.sp, color = NirogColor.inkTertiary)
    val allDates = remember(data) { data.series.flatMap { s -> s.points.map { it.date } }.distinct().sorted() }

    Column(modifier) {
        if (data.series.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                data.series.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(seriesColors[i % seriesColors.size], CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(s.label, fontSize = 12.sp, color = NirogColor.inkTertiary)
                    }
                }
            }
        }
        // What the member tapped, read out loud as it changes.
        Text(
            selected?.let { TrendText.describeDay(data, it) } ?: "Tap a point to see the day and the exact value.",
            fontSize = 13.sp, fontWeight = if (selected != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected != null) NirogColor.forest else NirogColor.inkTertiary,
            modifier = Modifier.padding(bottom = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .semantics { role = Role.Image; contentDescription = TrendText.spokenSummary(data) }
                .pointerInput(data) {
                    awaitPointerEventScope {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: continue
                            if (!change.pressed || allDates.isEmpty()) continue
                            val left = 40.dp.toPx(); val right = size.width - 8.dp.toPx()
                            val dayFraction = ((change.position.x - left) / (right - left)).coerceIn(0f, 1f)
                            val target = data.firstDate.plusDays((dayFraction * (totalDays - 1)).toLong())
                            selected = allDates.minByOrNull { abs(ChronoUnit.DAYS.between(it, target)) }
                        }
                    }
                },
        ) {
            val left = 40.dp.toPx(); val right = size.width - 8.dp.toPx()
            val top = 8.dp.toPx(); val bottom = size.height - 22.dp.toPx()
            fun x(date: LocalDate) = if (totalDays <= 1) (left + right) / 2 else left + (right - left) * ChronoUnit.DAYS.between(data.firstDate, date).toFloat() / (totalDays - 1)
            fun y(v: Double) = (bottom - ((v - axis.min) / axis.span).toFloat() * (bottom - top)).coerceIn(top, bottom)

            axis.ticks.forEach { tick ->
                val ty = y(tick)
                drawLine(Color(0xFFE4DDCD), Offset(left, ty), Offset(right, ty), 1f)
                val text = TrendText.axisLabel(data.metric, data.unit, tick)
                val layout = measurer.measure(text, labelStyle)
                drawText(measurer, text, Offset(left - 6.dp.toPx() - layout.size.width, ty - layout.size.height / 2f), labelStyle)
            }
            xLabels.forEach { label ->
                val layout = measurer.measure(label.text, labelStyle)
                val lx = (x(label.date) - layout.size.width / 2f).coerceIn(left - 8.dp.toPx(), size.width - layout.size.width.toFloat())
                drawText(measurer, label.text, Offset(lx, bottom + 4.dp.toPx()), labelStyle)
            }
            data.series.forEachIndexed { i, s ->
                val color = seriesColors[i % seriesColors.size]
                // A long silence breaks the line: nothing is drawn across days the member did not log.
                TrendBuilder.segments(s.points, data.range).forEach { segment ->
                    segment.zipWithNext().forEach { (a, b) -> drawLine(color, Offset(x(a.date), y(a.value)), Offset(x(b.date), y(b.value)), 3.dp.toPx()) }
                }
                s.points.forEach { p ->
                    val isSelected = p.date == selected
                    drawCircle(color, if (isSelected) 6.dp.toPx() else 3.5.dp.toPx(), Offset(x(p.date), y(p.value)))
                    if (isSelected) drawCircle(Color.White, 2.5.dp.toPx(), Offset(x(p.date), y(p.value)))
                }
            }
            selected?.let { sel -> drawLine(NirogColor.forest.copy(alpha = 0.35f), Offset(x(sel), top), Offset(x(sel), bottom), 1.dp.toPx()) }
        }
        TextButton(onClick = { showList = !showList }) {
            Text(if (showList) "Hide values" else "Show values as a list", fontSize = 12.sp, color = NirogColor.forestSoft)
        }
        if (showList) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                allDates.asReversed().forEach { date ->
                    Text(TrendText.describeDay(data, date)?.replace("\n", " · ") ?: "", fontSize = 13.sp, color = NirogColor.inkPrimary)
                }
            }
        }
    }
}

/**
 * One row of a metric's history. Shows Edit only while the 60-minute window is open, says why when it
 * is not (imported readings are fixed in their source app), and never shows a dead button.
 */
@Composable
fun HealthHistoryRow(
    title: String,
    subtitle: String,
    entry: HealthEntry,
    nowMillis: Long,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    note: String? = null,
) {
    val editability = EditWindow.editability(entry, nowMillis)
    Card(
        modifier = modifier.fillMaxWidth().border(0.5.dp, NirogColor.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, color = NirogColor.forest, fontSize = 15.sp)
                    Text(subtitle, fontSize = 12.sp, color = NirogColor.inkTertiary)
                }
                if (badge != null) {
                    Box(Modifier.background(NirogColor.surfaceNeutral, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NirogColor.forest)
                    }
                }
                if (editability is Editability.Editable) {
                    TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Edit", color = NirogColor.forestSoft, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (note != null) Text(note, fontSize = 12.sp, color = NirogColor.statusAttention, modifier = Modifier.padding(top = 4.dp))
            // Only explain an imported entry; an ordinary old entry just has no Edit button.
            (editability as? Editability.ImportedReadOnly)?.let {
                Text(EditWindow.hint(it) ?: "", fontSize = 12.sp, color = NirogColor.inkTertiary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Fixed explanation shown once under a history list so members know why older rows have no Edit. */
@Composable
fun EditWindowFootnote(modifier: Modifier = Modifier) {
    Text("You can edit an entry for 60 minutes after you log it.", fontSize = 12.sp, color = NirogColor.inkTertiary, modifier = modifier.padding(top = 4.dp))
}

/**
 * Shell shared by every correction dialog: a title, the fields, Save/Cancel, a spinner while the server
 * decides, and a plain-language error if it says no (for example, the 60 minutes ended while the dialog was open).
 */
@Composable
fun CorrectionDialog(
    title: String,
    canSave: Boolean,
    onDismiss: () -> Unit,
    onSave: (report: (String?) -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = NirogColor.forest) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
                error?.let { Text(it, fontSize = 13.sp, color = NirogColor.statusCritical, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave && !saving,
                colors = ButtonDefaults.buttonColors(containerColor = NirogColor.forestSoft),
                modifier = Modifier.heightIn(min = 48.dp),
                onClick = {
                    saving = true; error = null
                    onSave { message ->
                        saving = false
                        if (message == null) onDismiss() else error = message
                    }
                },
            ) { Text(if (saving) "Saving…" else "Save", color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel", color = NirogColor.inkTertiary) } },
    )
}

/** A 12-hour clock with AM/PM for choosing a time of day. Returns hour (0-23) and minute. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockPickerDialog(title: String, initialHour: Int, initialMinute: Int, onDismiss: () -> Unit, onConfirm: (hour: Int, minute: Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = NirogColor.forest) },
        text = { TimePicker(state = state) },
        confirmButton = {
            Button(onClick = { onConfirm(state.hour, state.minute) }, colors = ButtonDefaults.buttonColors(containerColor = NirogColor.forestSoft), modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Set ${ClockText.format12(state.hour, state.minute)}", color = Color.White)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel", color = NirogColor.inkTertiary) } },
    )
}

/** "Showing saved data" - only after a couple of seconds, so the normal cache-then-server load never flashes it. */
@Composable
fun StaleDataNotice(health: HealthUiState, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(health.isStale, health.isLoading) {
        if (health.isStale && !health.isLoading) { delay(2500); show = true } else show = false
    }
    if (show) {
        Text(
            "Showing your saved data. It will update when you're back online.",
            fontSize = 12.sp, color = NirogColor.inkTertiary,
            modifier = modifier.fillMaxWidth().background(NirogColor.surfaceNeutral, RoundedCornerShape(10.dp)).padding(10.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
