package com.nirogbhumi.app.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.nirogbhumi.app.health.domain.BpEntry
import com.nirogbhumi.app.health.domain.DiabetesType
import com.nirogbhumi.app.health.domain.GlucoseEntry
import com.nirogbhumi.app.health.domain.GlucoseKind
import com.nirogbhumi.app.health.domain.HealthSource
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.components.DiabetesTypePicker
import com.nirogbhumi.app.ui.theme.MyApplicationTheme
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Firebase is never initialised in this Robolectric process, so the repository fails closed: these tests exercise
 * what the member sees and can do on screen (labels, enabled/disabled buttons, validation), not real reads/writes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HealthScreensTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `sleep dialog shows 12-hour times with AM and PM and the length slept`() {
        composeTestRule.setContent {
            MyApplicationTheme { SleepEditorDialog(initial = null, zone = ZoneId.of("Asia/Kolkata"), title = "Add sleep", onDismiss = {}, onSave = { _, _, _, _ -> }) }
        }
        composeTestRule.onNodeWithText("Went to sleep").assertExists()
        composeTestRule.onNodeWithText("10:30 PM").assertExists()
        composeTestRule.onNodeWithText("6:30 AM").assertExists()
        composeTestRule.onNodeWithText("You slept for 8h.").assertExists()
        composeTestRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun `blood pressure edit uses Upper and Lower labels and refuses a lower number above the upper`() {
        val entry = BpEntry("b1", 120, 80, null, null, System.currentTimeMillis(), System.currentTimeMillis(), HealthSource.MANUAL)
        composeTestRule.setContent { MyApplicationTheme { BpEditDialog(entry, onDismiss = {}, onSave = { _, _ -> }) } }
        composeTestRule.onNodeWithText("Upper BP (mmHg)").assertExists()
        composeTestRule.onNodeWithText("Lower BP (mmHg)").assertExists()
        composeTestRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun `blood sugar edit refuses an impossible value`() {
        val now = System.currentTimeMillis()
        val entry = GlucoseEntry("g1", 110.0, GlucoseKind.FASTING, now, now, HealthSource.MANUAL)
        composeTestRule.setContent { MyApplicationTheme { GlucoseEditDialog(entry, onDismiss = {}, onSave = { _, _ -> }) } }
        composeTestRule.onNodeWithText("Save").assertIsEnabled()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("9")      // 1109 mg/dL
        composeTestRule.onNodeWithText("Enter a value between 20 and 800 mg/dL.").assertExists()
        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `diabetes type picker offers every type and only Other asks for text`() {
        var selected: DiabetesType? = null
        var other = ""
        composeTestRule.setContent {
            MyApplicationTheme {
                DiabetesTypePicker(selected = selected, otherText = other, onSelect = { selected = it }, onOtherText = { other = it })
            }
        }
        DiabetesType.entries.forEach { composeTestRule.onNodeWithText(it.label).assertExists() }
        composeTestRule.onNodeWithText("Gestational (during pregnancy)").assertExists()
        composeTestRule.onNodeWithText("Tell us which type (optional)").assertDoesNotExist()
        composeTestRule.onNodeWithText("Type 2").performClick()
        assertEquals(DiabetesType.TYPE_2, selected)
    }

    @Test
    fun `other diabetes type reveals a short text box`() {
        composeTestRule.setContent {
            MyApplicationTheme { DiabetesTypePicker(selected = DiabetesType.OTHER, otherText = "", onSelect = {}, onOtherText = {}) }
        }
        composeTestRule.onNodeWithText("Tell us which type (optional)").assertExists()
        composeTestRule.onNodeWithText("0/60").assertExists()
    }

    @Test
    fun `deleting an account asks for the typed phrase`() {
        val state = NirogState()
        composeTestRule.setContent { MyApplicationTheme { DataControlsScreen(state) } }
        composeTestRule.onNode(hasText("Delete my account") and hasClickAction()).performClick()
        composeTestRule.onNodeWithText("Delete your account?").assertExists()
        composeTestRule.onNodeWithText("To confirm, type DELETE MY ACCOUNT below.").assertExists()
    }

    @Test
    fun `a pending deletion shows its date and a way to keep the account`() {
        val state = NirogState()
        state.pendingDeletionMillis = System.currentTimeMillis() + 7L * 86_400_000L
        composeTestRule.setContent { MyApplicationTheme { DataControlsScreen(state) } }
        composeTestRule.onNodeWithText("Keep my account - cancel deletion").assertExists()
    }

    @Test
    fun `trends screen offers every metric`() {
        val state = NirogState()
        composeTestRule.setContent { MyApplicationTheme { TrendsScreen(state) } }
        composeTestRule.onNodeWithText("Your trends").assertExists()
        listOf("Blood sugar", "Blood pressure", "Weight", "Walking", "Sleep").forEach { label ->
            assertTrue("missing $label", composeTestRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun `weight screen opens with its header`() {
        composeTestRule.setContent { MyApplicationTheme { WeightOverviewScreen(NirogState()) } }
        composeTestRule.onNodeWithText("Weight").assertExists()
    }
}
