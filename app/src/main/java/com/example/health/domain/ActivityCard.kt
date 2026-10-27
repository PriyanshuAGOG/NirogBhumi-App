package com.nirogbhumi.app.health.domain

import java.util.Locale

/**
 * What the Today and Track "walking and activity" cards say. Steps (counted by a phone or watch) and minutes
 * (a walk or workout the member timed or typed in) are different records, so the card shows whichever exists and
 * says which one it is. It only says "No data yet" when there is neither.
 */
data class ActivityCardText(val value: String, val unit: String, val note: String, val hasData: Boolean) {
    /** One line for tiles that have a single text slot, e.g. "6,200 steps" or "30 min". */
    val oneLine: String get() = if (!hasData) value else if (unit.isBlank()) value else "$value $unit"
}

object ActivityCard {
    fun forToday(steps: Long, minutes: Int): ActivityCardText {
        val stepsText = String.format(Locale.US, "%,d", steps)
        return when {
            steps > 0 && minutes > 0 -> ActivityCardText(stepsText, "steps", "Plus $minutes min logged", true)
            steps > 0 -> ActivityCardText(stepsText, "steps", "Today", true)
            minutes > 0 -> ActivityCardText(minutes.toString(), "min", "Logged today", true)
            else -> ActivityCardText("—", "", "No data yet", false)
        }
    }
}
