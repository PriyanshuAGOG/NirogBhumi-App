package com.nirogbhumi.app.health.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileChoicesTest {
    @Test fun newCodesAreReadBack() {
        assertEquals(DiabetesAnswer(DiabetesType.GESTATIONAL, null), DiabetesTypes.fromStored("gestational", null, null))
        assertEquals(DiabetesAnswer(DiabetesType.OTHER, "MODY"), DiabetesTypes.fromStored("other", "  MODY ", null))
    }

    @Test fun olderDisplayTextStillMaps() {
        assertEquals(DiabetesType.NONE, DiabetesTypes.fromStored(null, null, "None")!!.type)
        assertEquals(DiabetesType.PREDIABETES, DiabetesTypes.fromStored(null, null, "Pre-diabetic")!!.type)
        assertEquals(DiabetesType.TYPE_2, DiabetesTypes.fromStored(null, null, "Type 2")!!.type)
        assertEquals(DiabetesType.TYPE_2, DiabetesTypes.fromStored(null, null, "Type 2 diabetes")!!.type)
        assertEquals(DiabetesType.TYPE_1, DiabetesTypes.fromStored(null, null, "Type 1")!!.type)
        assertEquals(DiabetesType.GESTATIONAL, DiabetesTypes.fromStored(null, null, "Gestational")!!.type)
        assertEquals(DiabetesType.NOT_SURE, DiabetesTypes.fromStored(null, null, "Not sure")!!.type)
    }

    @Test fun unknownOlderTextIsKeptAsOtherNotDropped() {
        val a = DiabetesTypes.fromStored(null, null, "LADA")!!
        assertEquals(DiabetesType.OTHER, a.type)
        assertEquals("LADA", a.otherText)
        assertEquals("Other: LADA", a.describe())
    }

    @Test fun nothingStoredMeansNotAnswered() {
        assertNull(DiabetesTypes.fromStored(null, null, null))
        assertNull(DiabetesTypes.fromStored("", null, "  "))
    }

    @Test fun otherTextIsSanitisedAndCapped() {
        assertEquals("a b", DiabetesTypes.sanitizeOther("a\n\t b"))
        assertEquals("script", DiabetesTypes.sanitizeOther("<script>"))
        assertEquals(60, DiabetesTypes.sanitizeOther("x".repeat(200))!!.length)
        assertNull(DiabetesTypes.sanitizeOther("   "))
        assertNull(DiabetesTypes.sanitizeOther(null))
    }

    @Test fun storedFormCarriesCodeTextAndReadableLabel() {
        val m = DiabetesTypes.toStored(DiabetesAnswer(DiabetesType.OTHER, "MODY"))
        assertEquals("other", m["diabetesType"]); assertEquals("MODY", m["diabetesTypeOther"]); assertEquals("Other: MODY", m["diabetesStatus"])
        val t2 = DiabetesTypes.toStored(DiabetesAnswer(DiabetesType.TYPE_2, "ignored"))
        assertEquals("type2", t2["diabetesType"]); assertNull(t2["diabetesTypeOther"]); assertEquals("Type 2", t2["diabetesStatus"])
    }

    @Test fun goalsAcceptCodesAndEveryOlderLabel() {
        val read = HealthGoals.fromStored(listOf("Control sugar", "Improve lifestyle", "Reverse diabetes journey", "Track parent’s health", "Improve BP", "Sleep better", "Walk more", "Join a program", "control_sugar", "nonsense", 5))
        assertEquals(listOf(HealthGoal.CONTROL_SUGAR, HealthGoal.IMPROVE_LIFESTYLE, HealthGoal.HELP_FAMILY, HealthGoal.LOWER_BP, HealthGoal.SLEEP_BETTER, HealthGoal.WALK_MORE, HealthGoal.JOIN_PROGRAM), read)
    }

    @Test fun olderSingleGoalChoicesMap() {
        assertEquals(listOf(HealthGoal.CONTROL_SUGAR), HealthGoals.fromStored(listOf("Manage blood sugar levels", "Control sugar and reverse naturally")))
        assertEquals(listOf(HealthGoal.IMPROVE_LIFESTYLE), HealthGoals.fromStored(listOf("Improve overall metabolic health")))
        assertEquals(listOf(HealthGoal.HELP_FAMILY), HealthGoals.fromStored(listOf("Track and manage parent's diabetes")))
    }

    @Test fun goalsStoreAsCodes() {
        assertEquals(listOf("walk_more", "sleep_better"), HealthGoals.toStored(listOf(HealthGoal.WALK_MORE, HealthGoal.SLEEP_BETTER, HealthGoal.WALK_MORE)))
        assertEquals(emptyList<HealthGoal>(), HealthGoals.fromStored(null))
    }

    @Test fun everyGoalHasPlainLabelAndUniqueCode() {
        assertEquals(HealthGoal.entries.size, HealthGoal.entries.map { it.wire }.toSet().size)
        for (g in HealthGoal.entries) assert(g.label.isNotBlank() && !g.label.contains("metabolic", ignoreCase = true)) { g.label }
    }
}
