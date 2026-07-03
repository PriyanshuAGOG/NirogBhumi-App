package com.nirogbhumi.app.update

/**
 * Semantic version comparison - never compare version strings directly
 * (e.g. "1.0.9" < "1.0.10" lexically fails since '1' > '1' is false but
 * "10" < "9" as strings). Segments are compared numerically; a missing
 * segment is treated as 0 ("1.2" == "1.2.0"); a non-numeric segment
 * (a stray "-beta" suffix etc.) falls back to 0 for that segment rather
 * than throwing, since a malformed version string from a bad release
 * should never crash the update check.
 */
object VersionChecker {
    /** Returns negative if [a] < [b], zero if equal, positive if [a] > [b]. */
    fun compare(a: String, b: String): Int {
        val segmentsA = segments(a)
        val segmentsB = segments(b)
        val length = maxOf(segmentsA.size, segmentsB.size)
        for (i in 0 until length) {
            val partA = segmentsA.getOrElse(i) { 0 }
            val partB = segmentsB.getOrElse(i) { 0 }
            if (partA != partB) return partA.compareTo(partB)
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    private fun segments(version: String): List<Int> =
        version.trim()
            .substringBefore('-') // drop a "-beta"/"-rc1" style suffix before splitting
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
}
