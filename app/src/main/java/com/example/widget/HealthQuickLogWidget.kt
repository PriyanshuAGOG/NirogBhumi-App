package com.nirogbhumi.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nirogbhumi.app.MainActivity

// Custom actions read back in MainActivity.onCreate/onNewIntent - a plain
// Intent + action string, not Glance's ActionParameters-to-extras
// marshalling, so a regular Activity can read it with zero Glance-specific
// knowledge at all (just intent.action, the same mechanism this app's push
// notifications and the existing "route" deep-link extra already use).
const val ACTION_OPEN_QUICK_LOG_SUGAR = "com.nirogbhumi.app.ACTION_OPEN_QUICK_LOG_SUGAR"
const val ACTION_OPEN_QUICK_LOG_BP = "com.nirogbhumi.app.ACTION_OPEN_QUICK_LOG_BP"

private val LAST_VALUE_KEY = intPreferencesKey("last_fasting_sugar_value")
private val LAST_STATUS_KEY = stringPreferencesKey("last_fasting_sugar_status")
private val LAST_LOGGED_AT_KEY = longPreferencesKey("last_fasting_sugar_logged_at")

private val Paper = Color(0xFFF7F5EC)
private val CardWhite = Color(0xFFFFFFFF)
private val InkPrimary = Color(0xFF1B2219)
private val InkSecondary = Color(0xFF526057)
private val InkMuted = Color(0xFF9AA396)
private val Forest = Color(0xFF3F7D58)
private val ForestDeep = Color(0xFF2A5A3E)
private val HairlineColor = Color(0xFFE7E2D3)
// Same status palette SugarLogHistoryRow already uses in-app, reused here so
// a reading looks the same whether you're glancing at the widget or looking
// inside the app - one status vocabulary, not two.
private val StatusHigh = Color(0xFFBA1A1A)
private val StatusLow = Color(0xFF43242A)
private val StatusNormal = Color(0xFF426820)

private fun statusColor(status: String?): Color = when (status) {
    "High" -> StatusHigh
    "Low" -> StatusLow
    else -> StatusNormal
}

/**
 * Home-screen widget: shows the last-logged fasting sugar reading (read from
 * a tiny per-widget Preferences cache, not a live Firestore listener - a
 * widget's execution window is too short-lived and infrequent for that) plus
 * two tappable rows that jump straight into the app's existing quick-log
 * entry points, instead of pretending a home-screen tile could safely
 * capture a precise health value itself with no numeric keypad and no way
 * to see or correct a fat-fingered tap before it's saved.
 */
class HealthQuickLogWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val lastValue = prefs[LAST_VALUE_KEY]
            val lastStatus = prefs[LAST_STATUS_KEY]
            val loggedAt = prefs[LAST_LOGGED_AT_KEY]

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Paper))
                    .cornerRadius(24.dp)
                    .padding(16.dp)
                    // The whole surface opens the app - previously only the two
                    // small buttons were tappable and everything else was a dead
                    // zone, which reads as "the widget is broken" the moment
                    // someone taps the big number instead of a button. The two
                    // buttons below keep their own more-specific actions (a
                    // child's click wins over this parent one).
                    .clickable(actionStartActivity(quickLogIntent(context, null)))
            ) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = GlanceModifier
                            .background(ColorProvider(Forest))
                            .cornerRadius(8.dp)
                            .width(24.dp)
                            .height(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("N", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorProvider(Color.White)))
                    }
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        "Nirog Bhumi",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorProvider(InkSecondary)),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Text("QUICK LOG", style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium, color = ColorProvider(InkMuted)))
                }
                Spacer(modifier = GlanceModifier.height(12.dp))
                HairlineDivider()
                Spacer(modifier = GlanceModifier.height(12.dp))

                Text("FASTING SUGAR", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ColorProvider(InkMuted)))
                Spacer(modifier = GlanceModifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (lastValue != null) "$lastValue" else "--",
                        style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = ColorProvider(if (lastValue != null) statusColor(lastStatus) else InkPrimary)),
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Column {
                        Text(
                            if (lastValue != null) "mg/dL" else "no reading yet",
                            style = TextStyle(fontSize = 12.sp, color = ColorProvider(InkMuted)),
                        )
                        Spacer(modifier = GlanceModifier.height(4.dp))
                    }
                }
                Spacer(modifier = GlanceModifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (lastValue != null && lastStatus != null) {
                        Box(
                            modifier = GlanceModifier
                                .background(ColorProvider(statusColor(lastStatus).copy(alpha = 0.14f)))
                                .cornerRadius(8.dp)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(lastStatus, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ColorProvider(statusColor(lastStatus))))
                        }
                        Spacer(modifier = GlanceModifier.width(8.dp))
                    }
                    Text(relativeLabel(loggedAt), style = TextStyle(fontSize = 11.sp, color = ColorProvider(InkMuted)))
                }

                Spacer(modifier = GlanceModifier.height(14.dp))

                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    QuickLogAction(
                        label = "Log sugar",
                        primary = true,
                        modifier = GlanceModifier.defaultWeight(),
                        onClick = actionStartActivity(quickLogIntent(context, ACTION_OPEN_QUICK_LOG_SUGAR)),
                    )
                    Spacer(modifier = GlanceModifier.width(10.dp))
                    QuickLogAction(
                        label = "Log BP",
                        primary = false,
                        modifier = GlanceModifier.defaultWeight(),
                        onClick = actionStartActivity(quickLogIntent(context, ACTION_OPEN_QUICK_LOG_BP)),
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun HairlineDivider() {
    Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(HairlineColor))) {}
}

@androidx.compose.runtime.Composable
private fun QuickLogAction(label: String, primary: Boolean, modifier: GlanceModifier, onClick: androidx.glance.action.Action) {
    Box(
        modifier = modifier
            .background(ColorProvider(if (primary) Forest else CardWhite))
            .cornerRadius(14.dp)
            .padding(vertical = 12.dp)
            .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorProvider(if (primary) Color.White else ForestDeep)),
        )
    }
}

// CLEAR_TOP + SINGLE_TOP matter as much as NEW_TASK here: without them a tap
// while the app is already running makes the system stack a SECOND
// MainActivity instance (launchMode is standard) with its own fresh
// NirogState - re-splash, re-routing, and the tapped action applied to a
// state object the user never sees settle. With them, the running instance
// receives the tap in onNewIntent and reacts instantly.
private fun quickLogIntent(context: Context, action: String?): Intent =
    Intent(context, MainActivity::class.java).apply {
        if (action != null) this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

private fun relativeLabel(loggedAtMillis: Long?): String {
    if (loggedAtMillis == null) return "Tap Log sugar to start"
    val hours = (System.currentTimeMillis() - loggedAtMillis) / 3_600_000
    return when {
        hours < 1 -> "Just now"
        hours < 24 -> "${hours}h ago"
        else -> "${hours / 24}d ago"
    }
}

class HealthQuickLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HealthQuickLogWidget()
}

/**
 * Called right after a fasting sugar reading is actually saved (both
 * QuickLogFastingOverlay and Daily Check-in's sugar step) - a no-op if the
 * widget was never added to a home screen (getGlanceIds returns empty).
 * status is whatever the caller already computed ("High"/"Normal"/"Low") -
 * deliberately not recomputed here so the widget never silently drifts from
 * whichever threshold definition each logging flow already uses.
 */
suspend fun updateHealthQuickLogWidget(context: Context, valueMgDl: Int, status: String) {
    val manager = GlanceAppWidgetManager(context)
    val ids = manager.getGlanceIds(HealthQuickLogWidget::class.java)
    for (id in ids) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { current ->
            current.toMutablePreferences().apply {
                this[LAST_VALUE_KEY] = valueMgDl
                this[LAST_STATUS_KEY] = status
                this[LAST_LOGGED_AT_KEY] = System.currentTimeMillis()
            }
        }
        HealthQuickLogWidget().update(context, id)
    }
}

/** Whether the device's launcher supports the direct "pin this widget" request below (API 26+, most modern launchers). */
fun isPinWidgetSupported(context: Context): Boolean {
    val appWidgetManager = context.getSystemService(AppWidgetManager::class.java) ?: return false
    return appWidgetManager.isRequestPinAppWidgetSupported
}

/**
 * Lets onboarding (and Profile settings) offer to add the widget from inside
 * the app itself, instead of only ever being discoverable by already knowing
 * to long-press the home screen and dig through the widget picker. The
 * launcher still shows its own system confirmation before actually placing
 * it - this only sends the request, it can't silently place a widget.
 */
fun requestPinQuickLogWidget(context: Context): Boolean {
    val appWidgetManager = context.getSystemService(AppWidgetManager::class.java) ?: return false
    if (!appWidgetManager.isRequestPinAppWidgetSupported) return false
    val provider = ComponentName(context, HealthQuickLogWidgetReceiver::class.java)
    return runCatching { appWidgetManager.requestPinAppWidget(provider, null, null) }.getOrDefault(false)
}
