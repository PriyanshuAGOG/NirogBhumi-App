package com.nirogbhumi.app.health.domain

/**
 * How long a member can correct a reading they typed in.
 *
 * The same 60 minutes is enforced by Firestore Security Rules against the server's clock
 * (see firebase/firestore.rules, `withinCorrectionWindow`), so this is only the UI mirror: it
 * decides whether to *offer* the Edit action. A phone with the wrong clock can show Edit
 * for a reading the server then refuses; the editor explains that in plain words.
 */
const val EDIT_WINDOW_MINUTES = 60L
const val EDIT_WINDOW_MILLIS = EDIT_WINDOW_MINUTES * 60_000L

sealed interface Editability {
    /** Can be corrected for [remainingMillis] more (the full window for a write the server has not stamped yet). */
    data class Editable(val remainingMillis: Long) : Editability
    /** The 60 minutes have passed. */
    data object Expired : Editability
    /** Came from Health Connect or a device: fix it in the source app, not here. */
    data class ImportedReadOnly(val source: HealthSource) : Editability
}

object EditWindow {
    fun editability(source: HealthSource, createdAtMillis: Long?, nowMillis: Long): Editability {
        if (source.isImported) return Editability.ImportedReadOnly(source)
        if (createdAtMillis == null) return Editability.Editable(EDIT_WINDOW_MILLIS)
        val remaining = createdAtMillis + EDIT_WINDOW_MILLIS - nowMillis
        // The window is half-open: exactly 60 minutes is already too late, matching `request.time < createdAt + 60m`.
        return if (remaining > 0) Editability.Editable(remaining) else Editability.Expired
    }

    fun editability(entry: HealthEntry, nowMillis: Long): Editability =
        editability(entry.source, entry.createdAtMillis, nowMillis)

    fun isEditable(entry: HealthEntry, nowMillis: Long): Boolean = editability(entry, nowMillis) is Editability.Editable

    /** Short, non-technical hint for a history row; null when there is nothing useful to say. */
    fun hint(editability: Editability): String? = when (editability) {
        is Editability.Editable -> null
        Editability.Expired -> "Editing is available for 60 minutes after you log an entry."
        is Editability.ImportedReadOnly -> "This entry came from Health Connect. Update it in the source app."
    }
}
