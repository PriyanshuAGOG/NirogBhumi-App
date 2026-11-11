package com.nirogbhumi.app.data

/**
 * The parts of error reporting that need no Android: what a report may contain and how often the same one may be sent.
 * (The server cleans everything again; this is the first line so nothing sensitive even leaves the phone.)
 */
object ClientErrorRules {
    const val MIN_GAP_MILLIS = 60_000L
    private val phoneLike = Regex("\\+?\\d[\\d\\s-]{7,}\\d")
    private val emailLike = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    fun clean(text: String?, max: Int): String =
        text.orEmpty().replace(emailLike, "[email]").replace(phoneLike, "[number]").replace(Regex("\\s+"), " ").trim().take(max)

    /** "code 123456 expired" and "code 654321 expired" are the same problem. */
    fun fingerprint(screen: String, code: String?, message: String): String =
        "$screen|${code.orEmpty()}|${message.replace(Regex("\\d+"), "#")}".take(240)

    /** Lets a given problem through at most once per [MIN_GAP_MILLIS], so a retry loop cannot spam the server. */
    class Throttle(private val gapMillis: Long = MIN_GAP_MILLIS, private val capacity: Int = 50) {
        private val last = LinkedHashMap<String, Long>()

        @Synchronized
        fun allow(fingerprint: String, nowMillis: Long): Boolean {
            last[fingerprint]?.let { if (nowMillis - it < gapMillis) return false }
            last[fingerprint] = nowMillis
            while (last.size > capacity) last.remove(last.keys.first())
            return true
        }
    }

    /** One line describing where a crash happened, without the exception's message (which can carry personal text). */
    fun crashSummary(exceptionName: String, frames: List<String>): Pair<String, String> =
        "$exceptionName at ${frames.firstOrNull().orEmpty()}".take(200) to frames.take(4).joinToString(" <- ").take(300)
}
