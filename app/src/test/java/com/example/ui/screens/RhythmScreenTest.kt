package com.nirogbhumi.app.ui.screens

import androidx.compose.ui.test.hasContentDescription
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
 * Firebase is never initialized in this Robolectric process, so the
 * glucoseReadings listener fails closed with CloudResult.Failure and the
 * screen renders its real, honest empty state - the same state a genuinely
 * new member sees before their first reading, not a fabricated placeholder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RhythmScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders the non-punitive header and last-7-days section`() {
        composeTestRule.setContent { MyApplicationTheme { RhythmScreen(NirogState()) } }

        composeTestRule.onNodeWithText("Your rhythm").assertExists()
        composeTestRule.onNodeWithText("Not a streak. A pattern.").assertExists()
        composeTestRule.onNodeWithText("Last 7 days").assertExists()
    }

    @Test
    fun `back button returns to the dashboard`() {
        val state = NirogState()
        state.currentScreen = "rhythm"
        composeTestRule.setContent { MyApplicationTheme { RhythmScreen(state) } }

        composeTestRule.onNodeWithText("Your rhythm").assertExists()
        composeTestRule.onNode(hasContentDescription("Back")).performClick()

        assert(state.currentScreen == "dashboard") { "Back button should route to dashboard, was ${state.currentScreen}" }
    }
}
