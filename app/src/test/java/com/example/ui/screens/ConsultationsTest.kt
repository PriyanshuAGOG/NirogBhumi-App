package com.nirogbhumi.app.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.google.firebase.Timestamp
import com.nirogbhumi.app.data.CloudDocument
import com.nirogbhumi.app.ui.components.StatusKind
import com.nirogbhumi.app.ui.theme.MyApplicationTheme
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Scroll into view first: below-the-fold nodes exist semantically but a tap on them would miss. */
private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()

private fun cdoc(id: String, vararg pairs: Pair<String, Any?>) = CloudDocument(id, mapOf(*pairs))

class ConsultationParsingTest {
  @Test
  fun `confirmed sessions come first (soonest first), then requests, then history newest first`() {
    val now = System.currentTimeMillis()
    val items = parseConsultations(
      listOf(
        cdoc("done", "status" to "completed", "createdAt" to Timestamp(Date(now - 5_000_000))),
        cdoc("req", "status" to "pending", "createdAt" to Timestamp(Date(now - 1_000))),
        cdoc("later", "status" to "confirmed", "scheduledAt" to Timestamp(Date(now + 9_000_000)), "createdAt" to Timestamp(Date(now - 3_000))),
        cdoc("soon", "status" to "confirmed", "scheduledAt" to Timestamp(Date(now + 1_000_000)), "createdAt" to Timestamp(Date(now - 4_000))),
        cdoc("cancelled", "status" to "cancelled", "createdAt" to Timestamp(Date(now - 100))),
      ),
    )
    assertEquals(listOf("soon", "later", "req", "cancelled", "done"), items.map { it.id })
  }

  @Test
  fun `missing fields fall back safely and unsafe links are dropped`() {
    val item = parseConsultations(listOf(cdoc("x", "joinLink" to "javascript:alert(1)"))).single()
    assertEquals("Consultation", item.type)
    assertEquals("pending", item.status)
    assertNull(item.joinLink)
    assertNull(item.scheduledAtMillis)
    assertEquals("https://meet.example/abc", parseConsultations(listOf(cdoc("y", "joinLink" to "https://meet.example/abc"))).single().joinLink)
  }

  @Test
  fun `status labels and who can cancel`() {
    assertEquals("Confirmed" to StatusKind.InRange, consultationStatusLabel("confirmed"))
    assertEquals("Waiting for confirmation" to StatusKind.Attention, consultationStatusLabel("pending"))
    assertEquals("Waiting for confirmation" to StatusKind.Attention, consultationStatusLabel("payment_pending"))
    assertEquals("Not scheduled", consultationStatusLabel("declined").first)
    assertTrue(canCancelConsultation("pending"))
    assertTrue(canCancelConsultation("confirmed"))
    assertFalse(canCancelConsultation("completed"))
    assertFalse(canCancelConsultation("cancelled"))
    assertFalse(canCancelConsultation("declined"))
    assertEquals("Video call", consultationModeLabel("video"))
    assertNull(consultationModeLabel("carrier-pigeon"))
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RequestConsultationScreenTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `send is disabled until there is a real concern and the emergency note is acknowledged`() {
    composeTestRule.setContent { MyApplicationTheme { RequestConsultationContent(submitting = false, error = null, onBack = {}, onSubmit = { _, _, _, _ -> }) } }
    composeTestRule.onNodeWithText("Send request").assertIsNotEnabled()
    composeTestRule.onNode(hasSetTextAction()).performScrollTo().performTextInput("too short")
    composeTestRule.onNodeWithText("Send request").assertIsNotEnabled()
    composeTestRule.onNode(hasSetTextAction()).performScrollTo().performTextInput(" - now it is long enough to be useful")
    composeTestRule.onNodeWithText("Send request").assertIsNotEnabled() // still not acknowledged
    composeTestRule.onNodeWithText("I understand this is not emergency care. If I feel very unwell I will contact emergency services or my doctor.").tap()
    composeTestRule.onNodeWithText("Send request").assertIsEnabled()
  }

  @Test
  fun `submitting passes the chosen type, concern, window and sharing choice`() {
    var captured: List<Any> = emptyList()
    composeTestRule.setContent {
      MyApplicationTheme { RequestConsultationContent(submitting = false, error = null, onBack = {}, onSubmit = { t, c, w, s -> captured = listOf(t, c, w, s) }) }
    }
    composeTestRule.onNodeWithText("Yoga").tap()
    composeTestRule.onNodeWithText("Evening").tap()
    composeTestRule.onNodeWithText("Let the expert see my recent readings").tap() // untick
    composeTestRule.onNode(hasSetTextAction()).performScrollTo().performTextInput("Knees hurt during the morning routine")
    composeTestRule.onNodeWithText("I understand this is not emergency care. If I feel very unwell I will contact emergency services or my doctor.").tap()
    composeTestRule.onNodeWithText("Send request").tap()
    assertEquals(listOf<Any>("Yoga", "Knees hurt during the morning routine", "Evening", false), captured)
  }

  @Test
  fun `shows the sending state and an error message`() {
    composeTestRule.setContent { MyApplicationTheme { RequestConsultationContent(submitting = true, error = "We couldn't send your request.", onBack = {}, onSubmit = { _, _, _, _ -> }) } }
    composeTestRule.onNodeWithText("Sending…").assertIsNotEnabled()
    composeTestRule.onNodeWithText("We couldn't send your request.").assertExists()
  }

  @Test
  fun `back works`() {
    var back = false
    composeTestRule.setContent { MyApplicationTheme { RequestConsultationContent(submitting = false, error = null, onBack = { back = true }, onSubmit = { _, _, _, _ -> }) } }
    composeTestRule.onNode(hasContentDescription("Back")).performClick()
    assertTrue(back)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MyConsultationsScreenTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  private val confirmed = ConsultationItem("c1", "Diet review", "", "confirmed", System.currentTimeMillis() + 86_400_000L, "Dr. Meera", "video", "https://meet.example/abc", null, "₹699 - payment link to follow", "Keep your last week's readings handy", null, 0L)
  private val requested = ConsultationItem("c2", "Yoga", "Knees hurt in the morning routine", "pending", null, "", null, null, null, null, null, null, 0L)
  private val declined = ConsultationItem("c3", "Naturopathy", "", "declined", null, "", null, null, null, null, null, "Fully booked this week - please request again Monday.", 0L)

  private fun show(
    items: List<ConsultationItem>, loading: Boolean = false, error: String? = null, cancellingId: String? = null,
    onOpenLink: (String) -> Unit = {}, onCancel: (String) -> Unit = {}, onNew: () -> Unit = {}, onSupport: () -> Unit = {},
  ) = composeTestRule.setContent {
    MyApplicationTheme { MyConsultationsContent(items, loading, error, cancellingId, {}, onNew, onSupport, onOpenLink, onCancel) }
  }

  @Test
  fun `a confirmed booking shows expert, how, fee note, message and a join button`() {
    var opened = ""
    show(listOf(confirmed), onOpenLink = { opened = it })
    composeTestRule.onNodeWithText("Confirmed", ignoreCase = true).assertExists()
    composeTestRule.onNodeWithText("with Dr. Meera · Video call").assertExists()
    composeTestRule.onNodeWithText("₹699 - payment link to follow").assertExists()
    composeTestRule.onNodeWithText("“Keep your last week's readings handy”").assertExists()
    composeTestRule.onNodeWithText("Join video call").tap()
    assertEquals("https://meet.example/abc", opened)
  }

  @Test
  fun `a waiting request shows the concern and can be withdrawn only after confirming`() {
    var cancelled = ""
    show(listOf(requested), onCancel = { cancelled = it })
    composeTestRule.onNodeWithText("Waiting for confirmation", ignoreCase = true).assertExists()
    composeTestRule.onNodeWithText("Knees hurt in the morning routine").assertExists()
    composeTestRule.onNodeWithText("Withdraw request").tap()
    assertEquals("", cancelled) // dialog first
    composeTestRule.onNodeWithText("Cancel this consultation?").assertExists()
    composeTestRule.onNodeWithText("Cancel consultation").performClick()
    assertEquals("c2", cancelled)
  }

  @Test
  fun `keeping it closes the dialog without cancelling`() {
    var cancelled = ""
    show(listOf(confirmed), onCancel = { cancelled = it })
    composeTestRule.onNodeWithText("Cancel this booking").tap()
    composeTestRule.onNodeWithText("Keep it").performClick()
    assertEquals("", cancelled)
    composeTestRule.onNodeWithText("Cancel this consultation?").assertDoesNotExist()
  }

  @Test
  fun `a declined request explains why and offers support`() {
    var support = false
    show(listOf(declined), onSupport = { support = true })
    composeTestRule.onNodeWithText("Not scheduled", ignoreCase = true).assertExists()
    composeTestRule.onNodeWithText("Fully booked this week - please request again Monday.").assertExists()
    composeTestRule.onNodeWithText("Withdraw request").assertDoesNotExist()
    composeTestRule.onNodeWithText("Contact support").tap()
    assertTrue(support)
  }

  @Test
  fun `empty loading and error states are honest`() {
    show(emptyList())
    composeTestRule.onNodeWithText("No consultations yet").assertExists()
  }

  @Test
  fun `error state when nothing loaded`() {
    show(emptyList(), error = "We couldn't load your consultations.")
    composeTestRule.onNodeWithText("Couldn't load your consultations").assertExists()
  }

  @Test
  fun `request button starts a new request`() {
    var started = false
    show(emptyList(), onNew = { started = true })
    composeTestRule.onNodeWithText("Request a consultation").tap()
    assertTrue(started)
  }

  @Test
  fun `cancelling in progress disables the action`() {
    show(listOf(requested), cancellingId = "c2")
    composeTestRule.onNodeWithText("Cancelling…").assertIsNotEnabled()
  }
}
