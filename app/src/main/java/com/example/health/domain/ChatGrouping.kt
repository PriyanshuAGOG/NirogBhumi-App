package com.nirogbhumi.app.health.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the layout needs to know about one message. [atMillis] is null while a just-sent message waits for the server's time. */
data class ChatKey(val senderId: String, val atMillis: Long?)

/**
 * Where a message sits in the conversation. Messages from the same person within a few minutes read as one group: the
 * name shows once at the start, the time once at the end, and a day label appears when the day changes. This keeps each
 * bubble free of repeated names and timestamps.
 */
data class ChatPosition(val startsGroup: Boolean, val endsGroup: Boolean, val dayLabel: String?)

object ChatGrouping {
    const val GROUP_WINDOW_MILLIS = 5 * 60_000L
    private val sameYear = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val otherYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /** "Today", "Yesterday", "Mon 12 Oct", or "3 Mar 2025". */
    fun dayLabel(atMillis: Long, nowMillis: Long, zone: ZoneId): String {
        val day = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when {
            day == today -> "Today"
            day == today.minusDays(1) -> "Yesterday"
            day.year == today.year -> day.format(sameYear)
            else -> day.format(otherYear)
        }
    }

    /**
     * [newestFirst] is how chats are stored and shown (newest at the bottom of a reversed list). The result has one entry per
     * message, in the same order.
     */
    fun positions(newestFirst: List<ChatKey>, zone: ZoneId, nowMillis: Long): List<ChatPosition> {
        fun day(m: Long?): LocalDate? = m?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        fun together(a: ChatKey, b: ChatKey): Boolean {
            if (a.senderId != b.senderId) return false
            val am = a.atMillis; val bm = b.atMillis
            if (am == null || bm == null) return true               // a message still sending belongs with the one before it
            return day(am) == day(bm) && kotlin.math.abs(am - bm) <= GROUP_WINDOW_MILLIS
        }
        return newestFirst.mapIndexed { i, item ->
            val older = newestFirst.getOrNull(i + 1)
            val newer = newestFirst.getOrNull(i - 1)
            val thisDay = day(item.atMillis)
            val startsDay = thisDay != null && (older == null || day(older.atMillis).let { it != null && it != thisDay })
            ChatPosition(
                startsGroup = older == null || !together(older, item),
                endsGroup = newer == null || !together(item, newer),
                dayLabel = if (startsDay) dayLabel(item.atMillis!!, nowMillis, zone) else null,
            )
        }
    }
}
