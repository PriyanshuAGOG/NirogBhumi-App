package com.nirogbhumi.app.health

/**
 * Picks the single "one action for today" from real, data-backed signals -
 * never a fixed task shown forever. Priority favors whichever core habit the
 * app can actually detect the user hasn't done yet today, so the card keeps
 * changing in step with real usage instead of always saying the same thing.
 */
enum class TodayFocusActionId { CHECK_IN, LOG_READING, WALK, ALL_DONE }

data class TodayFocusAction(
    val id: TodayFocusActionId,
    val label: String,
    val doneLabel: String,
)

object TodayFocusEngine {
    fun pick(
        isProgramActive: Boolean,
        checkedInToday: Boolean,
        loggedReadingToday: Boolean,
        walkDoneToday: Boolean,
    ): TodayFocusAction = when {
        isProgramActive && !checkedInToday ->
            TodayFocusAction(TodayFocusActionId.CHECK_IN, "Complete today's check-in", "Checked in for today!")
        !loggedReadingToday ->
            TodayFocusAction(TodayFocusActionId.LOG_READING, "Log today's sugar reading", "Reading logged for today!")
        !walkDoneToday ->
            TodayFocusAction(TodayFocusActionId.WALK, "Walk 15 minutes after a meal", "Walk logged and completed!")
        else ->
            TodayFocusAction(TodayFocusActionId.ALL_DONE, "All caught up for today", "All caught up for today!")
    }
}
