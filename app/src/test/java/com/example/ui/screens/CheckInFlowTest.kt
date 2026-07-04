package com.nirogbhumi.app.ui.screens

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Firebase is never initialized in this Robolectric process, so
 * FirebaseHealthRepository's isCloudConfigured is false and every call
 * fails closed with CloudResult.Failure - these tests exercise the wizard's
 * pure navigation (which never touches the repository when a step is
 * skipped with a blank input), not real reads/writes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CheckInFlowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `opens on the sugar step with all four progress dots`() {
        composeTestRule.setContent { MyApplicationTheme { DailyCheckInScreen(NirogState()) } }

        composeTestRule.onNodeWithText("Daily Check-in").assertExists()
        composeTestRule.onNodeWithText("Step 1 of 4").assertExists()
        composeTestRule.onNodeWithText("Blood sugar").assertExists()
        composeTestRule.onNodeWithText("Fasting").assertExists()
        composeTestRule.onNodeWithText("Post-meal").assertExists()
        composeTestRule.onNodeWithText("HbA1c").assertExists()
    }

    @Test
    fun `skipping each step advances through the whole wizard in order`() {
        composeTestRule.setContent { MyApplicationTheme { DailyCheckInScreen(NirogState()) } }

        composeTestRule.onNodeWithText("Step 1 of 4").assertExists()
        composeTestRule.onNodeWithText("Skip this").performClick()

        composeTestRule.onNodeWithText("Step 2 of 4").assertExists()
        composeTestRule.onNodeWithText("Blood pressure").assertExists()
        composeTestRule.onNodeWithText("Skip this").performClick()

        composeTestRule.onNodeWithText("Step 3 of 4").assertExists()
        composeTestRule.onNodeWithText("Skip this").performClick()

        composeTestRule.onNodeWithText("Step 4 of 4").assertExists()
        composeTestRule.onNodeWithText("Skip & finish").performClick()

        // Step 4 (medication) skipped -> step 5, the closing summary, which
        // drops the "Step N of 4" progress header entirely.
        composeTestRule.onNodeWithText("Step 4 of 4").assertDoesNotExist()
    }

    @Test
    fun `tapping a sugar type chip selects it without crashing`() {
        composeTestRule.setContent { MyApplicationTheme { DailyCheckInScreen(NirogState()) } }

        composeTestRule.onNodeWithText("Post-meal").performClick()
        composeTestRule.onNodeWithText("Post-meal").assertExists()
    }
}
