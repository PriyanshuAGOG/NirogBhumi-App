package com.nirogbhumi.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileValidationTest {
    @Test
    fun blankFieldsAreValid_weWouldRatherHaveNothingThanAMadeUpValue() {
        assertNull(ProfileValidation.validate("", "", ""))
        assertNull(ProfileValidation.validate("  ", " ", " "))
    }

    @Test
    fun believableValuesPass() {
        assertNull(ProfileValidation.validate("42", "68.5", "165"))
        assertNull(ProfileValidation.validate("1", "20", "50"))
        assertNull(ProfileValidation.validate("120", "300", "250"))
    }

    @Test
    fun outOfRangeOrNonNumericValuesAreRejectedWithASpecificMessage() {
        assertNotNull(ProfileValidation.validate("0", "", ""))
        assertNotNull(ProfileValidation.validate("121", "", ""))
        assertNotNull(ProfileValidation.validate("abc", "", ""))
        assertNotNull(ProfileValidation.validate("", "19.9", ""))
        assertNotNull(ProfileValidation.validate("", "301", ""))
        assertNotNull(ProfileValidation.validate("", "", "49"))
        assertNotNull(ProfileValidation.validate("", "", "251"))
        assertEquals("Age should be between 1 and 120", ProfileValidation.validate("200", "70", "170"))
    }

    @Test
    fun numericOnlyStripsTextAndKeepsOneDecimalPoint() {
        assertEquals("42", ProfileValidation.numericOnly("4a2 ", allowDecimal = false))
        assertEquals("68.5", ProfileValidation.numericOnly("68.5", allowDecimal = true))
        assertEquals("68.5", ProfileValidation.numericOnly("6.8.5", allowDecimal = true))
        assertEquals("685", ProfileValidation.numericOnly("68.5", allowDecimal = false))
        assertEquals("123456", ProfileValidation.numericOnly("1234567890", allowDecimal = false))
    }
}
