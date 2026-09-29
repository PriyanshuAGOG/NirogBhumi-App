package com.nirogbhumi.app.ui

/**
 * Basic-profile validation shared by onboarding and Edit profile. Every field
 * is optional (blank is fine - we'd rather have no value than a made-up one),
 * but anything that is entered must be a believable number, because it feeds
 * insights and is printed on the shared Health File.
 */
object ProfileValidation {
    const val MIN_AGE = 1
    const val MAX_AGE = 120
    const val MIN_WEIGHT_KG = 20.0
    const val MAX_WEIGHT_KG = 300.0
    const val MIN_HEIGHT_CM = 50.0
    const val MAX_HEIGHT_CM = 250.0

    val GENDER_OPTIONS = listOf("Female", "Male", "Other", "Prefer not to say")

    /** Returns a short, member-facing message for the first problem found, or null if everything is fine. */
    fun validate(age: String, weightKg: String, heightCm: String): String? {
        if (age.isNotBlank()) {
            val value = age.trim().toIntOrNull()
            if (value == null || value < MIN_AGE || value > MAX_AGE) return "Age should be between $MIN_AGE and $MAX_AGE"
        }
        if (weightKg.isNotBlank()) {
            val value = weightKg.trim().toDoubleOrNull()
            if (value == null || value < MIN_WEIGHT_KG || value > MAX_WEIGHT_KG) return "Weight should be between ${MIN_WEIGHT_KG.toInt()} and ${MAX_WEIGHT_KG.toInt()} kg"
        }
        if (heightCm.isNotBlank()) {
            val value = heightCm.trim().toDoubleOrNull()
            if (value == null || value < MIN_HEIGHT_CM || value > MAX_HEIGHT_CM) return "Height should be between ${MIN_HEIGHT_CM.toInt()} and ${MAX_HEIGHT_CM.toInt()} cm"
        }
        return null
    }

    /** Keeps only digits (and at most one decimal point when [allowDecimal]) so a number field can't hold text. */
    fun numericOnly(raw: String, allowDecimal: Boolean): String {
        var seenDot = false
        return raw.filter { c ->
            when {
                c.isDigit() -> true
                allowDecimal && c == '.' && !seenDot -> { seenDot = true; true }
                else -> false
            }
        }.take(6)
    }
}
