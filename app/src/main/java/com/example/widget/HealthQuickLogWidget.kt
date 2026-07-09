package com.nirogbhumi.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
private val LAST_LOGGED_AT_KEY = longPreferencesKey("last_fasting_sugar_logged_at")

private val Paper = Color(0xFFF8F6EF)
private val InkPrimary = Color(0xFF1B2219)
private val InkSecondary = Color(0xFF526057)
private val InkMuted = Color(0xFF8B9285)
private val Forest = Color(0xFF3F7D58)

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
            val loggedAt = prefs[LAST_LOGGED_AT_KEY]

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Paper))
                    .cornerRadius(20.dp)
                    .padding(16.dp)
            ) {
                Text("Fasting sugar", style = TextStyle(fontWeight = FontWeight.Bold, color = ColorProvider(InkSecondary)))
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    if (lastValue != null) "$lastValue mg/dL" else "No reading yet",
                    style = TextStyle(fontWeight = FontWeight.Bold, color = ColorProvider(InkPrimary)),
                )
                Text(relativeLabel(loggedAt), style = TextStyle(color = ColorProvider(InkMuted)))
                Spacer(modifier = GlanceModifier.height(12.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    QuickLogAction(
                        label = "Log sugar",
                        modifier = GlanceModifier.defaultWeight(),
                        onClick = actionStartActivity(quickLogIntent(context, ACTION_OPEN_QUICK_LOG_SUGAR)),
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    QuickLogAction(
                        label = "Log BP",
                        modifier = GlanceModifier.defaultWeight(),
                        onClick = actionStartActivity(quickLogIntent(context, ACTION_OPEN_QUICK_LOG_BP)),
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun QuickLogAction(label: String, modifier: GlanceModifier, onClick: androidx.glance.action.Action) {
    Box(
        modifier = modifier
            .background(ColorProvider(Forest))
            .cornerRadius(12.dp)
            .padding(vertical = 10.dp)
            .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(fontWeight = FontWeight.Bold, color = ColorProvider(Color.White)))
    }
}

private fun quickLogIntent(context: Context, action: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
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
 */
suspend fun updateHealthQuickLogWidget(context: Context, valueMgDl: Int) {
    val manager = GlanceAppWidgetManager(context)
    val ids = manager.getGlanceIds(HealthQuickLogWidget::class.java)
    for (id in ids) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { current ->
            current.toMutablePreferences().apply {
                this[LAST_VALUE_KEY] = valueMgDl
                this[LAST_LOGGED_AT_KEY] = System.currentTimeMillis()
            }
        }
        HealthQuickLogWidget().update(context, id)
    }
}
