package com.nirogbhumi.app.health.domain

/**
 * Fixed answers for the health-profile questions, stored as stable codes (not display text) so
 * wording can change without orphaning saved profiles. Older profiles stored the display text
 * ("Pre-diabetic", "Type 2"), which [DiabetesTypes.fromStored] and [HealthGoals.fromStored] still understand.
 */
enum class DiabetesType(val wire: String, val label: String) {
    NONE("none", "No diabetes"),
    PREDIABETES("prediabetes", "Pre-diabetes"),
    TYPE_1("type1", "Type 1"),
    TYPE_2("type2", "Type 2"),
    GESTATIONAL("gestational", "Gestational (during pregnancy)"),
    OTHER("other", "Other"),
    NOT_SURE("not_sure", "Not sure"),
}

data class DiabetesAnswer(val type: DiabetesType, val otherText: String?) {
    /** Plain text for reports and PDFs, e.g. "Type 2" or "Other: MODY". */
    fun describe(): String = if (type == DiabetesType.OTHER && !otherText.isNullOrBlank()) "Other: $otherText" else type.label
}

object DiabetesTypes {
    const val MAX_OTHER_LENGTH = 60

    /** Free text kept short and single-line; control characters and markup-looking angle brackets removed. */
    fun sanitizeOther(text: String?): String? = text
        ?.filter { !it.isISOControl() && it != '<' && it != '>' }
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(MAX_OTHER_LENGTH)
        ?.takeIf { it.isNotEmpty() }

    private val legacy: Map<String, DiabetesType> = mapOf(
        "none" to DiabetesType.NONE, "no diabetes" to DiabetesType.NONE, "no" to DiabetesType.NONE,
        "pre-diabetic" to DiabetesType.PREDIABETES, "prediabetic" to DiabetesType.PREDIABETES, "prediabetes" to DiabetesType.PREDIABETES, "pre-diabetes" to DiabetesType.PREDIABETES,
        "type 1" to DiabetesType.TYPE_1, "type 1 diabetes" to DiabetesType.TYPE_1, "type1" to DiabetesType.TYPE_1,
        "type 2" to DiabetesType.TYPE_2, "type 2 diabetes" to DiabetesType.TYPE_2, "type2" to DiabetesType.TYPE_2,
        "gestational" to DiabetesType.GESTATIONAL,
        "not sure" to DiabetesType.NOT_SURE, "not_sure" to DiabetesType.NOT_SURE,
        "other" to DiabetesType.OTHER,
    )

    /**
     * Reads what a profile document holds: the new code in `diabetesType` (with `diabetesTypeOther`),
     * else the older display text in `diabetesStatus`. Text that matches nothing is kept as "Other: <text>"
     * rather than dropped, so nothing the member once wrote is lost.
     */
    fun fromStored(code: String?, other: String?, legacyStatus: String?): DiabetesAnswer? {
        code?.trim()?.lowercase()?.let { c -> DiabetesType.entries.firstOrNull { it.wire == c } }?.let { return DiabetesAnswer(it, sanitizeOther(other)) }
        val raw = legacyStatus?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        legacy[raw.lowercase()]?.let { return DiabetesAnswer(it, null) }
        return DiabetesAnswer(DiabetesType.OTHER, sanitizeOther(raw))
    }

    /** Fields to save: the new code (and text when Other), plus the readable label older screens and exports understand. */
    fun toStored(answer: DiabetesAnswer): Map<String, Any?> = mapOf(
        "diabetesType" to answer.type.wire,
        "diabetesTypeOther" to if (answer.type == DiabetesType.OTHER) sanitizeOther(answer.otherText) else null,
        "diabetesStatus" to answer.describe(),
    )
}

enum class HealthGoal(val wire: String, val label: String) {
    CONTROL_SUGAR("control_sugar", "Keep my blood sugar steady"),
    IMPROVE_LIFESTYLE("improve_lifestyle", "Build healthier daily habits"),
    LOWER_BP("lower_bp", "Keep my blood pressure healthy"),
    SLEEP_BETTER("sleep_better", "Sleep better"),
    WALK_MORE("walk_more", "Walk more"),
    MANAGE_WEIGHT("manage_weight", "Manage my weight"),
    HELP_FAMILY("help_family", "Look after a family member"),
    JOIN_PROGRAM("join_program", "Join a coaching program"),
}

object HealthGoals {
    private val legacy: Map<String, HealthGoal> = mapOf(
        "control sugar" to HealthGoal.CONTROL_SUGAR, "manage blood sugar levels" to HealthGoal.CONTROL_SUGAR,
        "control sugar and reverse naturally" to HealthGoal.CONTROL_SUGAR, "reverse diabetes journey" to HealthGoal.CONTROL_SUGAR,
        "improve lifestyle" to HealthGoal.IMPROVE_LIFESTYLE, "improve overall metabolic health" to HealthGoal.IMPROVE_LIFESTYLE,
        "improve bp" to HealthGoal.LOWER_BP,
        "sleep better" to HealthGoal.SLEEP_BETTER,
        "walk more" to HealthGoal.WALK_MORE,
        "track parent’s health" to HealthGoal.HELP_FAMILY, "track parent's health" to HealthGoal.HELP_FAMILY,
        "track and manage parent's diabetes" to HealthGoal.HELP_FAMILY,
        "join a program" to HealthGoal.JOIN_PROGRAM,
    )

    /** Accepts codes and every older display string; unknown entries are skipped; order kept, no duplicates. */
    fun fromStored(values: List<*>?): List<HealthGoal> = values.orEmpty()
        .mapNotNull { it as? String }
        .mapNotNull { raw ->
            val key = raw.trim().lowercase()
            HealthGoal.entries.firstOrNull { it.wire == key } ?: legacy[key]
        }
        .distinct()

    fun toStored(goals: Collection<HealthGoal>): List<String> = goals.map { it.wire }.distinct()
}
