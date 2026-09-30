package com.nirogbhumi.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Analytics must never carry health values or personal text. Every event name and every parameter name sent through
 * AnalyticsLogger is listed here; adding a new one fails this test until someone has decided it is safe to send
 * (event and collection names, counts, yes/no flags, hour of day - never a reading, a name, a message or a note).
 */
class AnalyticsEventsTest {
    private val allowedEvents = setOf(
        "log_added", "health_log_corrected", "health_log_edit_expired", "data_export_requested", "account_deletion_requested",
        "account_deletion_cancelled", "program_joined", "announcement_posted", "chat_message_sent", "consultation_cancelled", "checkin_completed",
    )
    private val allowedParams = setOf("log_type", "program_id", "is_reply", "has_photo", "has_audio", "has_voice", "hour", "streak")

    private fun kotlinSources(): List<File> {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "AnalyticsLogger.kt" }.toList()
    }

    @Test fun onlyReviewedEventsAndParametersAreSent() {
        val call = Regex("""AnalyticsLogger\.log\(\s*"([a-z_]+)"(?:\s*,\s*mapOf\((.*?)\))?\s*\)""", RegexOption.DOT_MATCHES_ALL)
        val key = Regex(""""([a-z_]+)"\s+to\s""")
        val seen = mutableListOf<String>()
        for (file in kotlinSources()) {
            val text = file.readText()
            for (m in call.findAll(text)) {
                val event = m.groupValues[1]
                seen += event
                assertTrue("New analytics event \"$event\" in ${file.name}: review it for personal data, then add it to this test", event in allowedEvents)
                for (k in key.findAll(m.groupValues[2])) {
                    val param = k.groupValues[1]
                    assertTrue("New analytics parameter \"$param\" on \"$event\" in ${file.name}: review it for personal data, then add it to this test", param in allowedParams)
                }
            }
            // Every call in the file must have been matched by the pattern above (so none slips past with an unusual shape).
            val total = Regex("""AnalyticsLogger\.log\(""").findAll(text).count()
            val matched = call.findAll(text).count()
            assertTrue("${file.name}: $total AnalyticsLogger.log calls but only $matched match the reviewed shape", total == matched)
        }
        assertTrue("expected to find the existing analytics calls", seen.size >= 8)
    }
}
