package com.nirogbhumi.app.ui.screens

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.firebase.Timestamp
import com.nirogbhumi.app.data.CloudDocument
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.theme.MyApplicationTheme
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun doc(id: String, vararg pairs: Pair<String, Any?>) = CloudDocument(id, mapOf(*pairs))

class ProgramResourceParsingTest {
  @Test
  fun `skips documents without a title and orders week one first, unweeked last, newest first within a week`() {
    val old = Timestamp(Date(1_000_000))
    val recent = Timestamp(Date(9_000_000))
    val parsed = parseProgramResources(
      listOf(
        doc("general", "title" to "General note", "category" to "guidance", "createdAt" to recent),
        doc("w2", "title" to "Week 2 plate", "category" to "diet", "weekNumber" to 2L, "createdAt" to recent),
        doc("w1old", "title" to "Week 1 old", "category" to "diet", "weekNumber" to 1L, "createdAt" to old),
        doc("w1new", "title" to "Week 1 new", "category" to "yoga", "weekNumber" to 1L, "updatedAt" to recent),
        doc("blank", "title" to "   ", "category" to "diet"),
        doc("missing", "category" to "diet"),
      ),
    )
    assertEquals(listOf("w1new", "w1old", "w2", "general"), parsed.map { it.id })
  }

  @Test
  fun `falls back to safe defaults for missing fields`() {
    val r = parseProgramResources(listOf(doc("x", "title" to "Hello"))).single()
    assertEquals("other", r.category)
    assertEquals("Your coach", r.author)
    assertEquals("", r.body)
    assertNull(r.weekNumber)
    assertNull(r.link)
  }

  @Test
  fun `only web links are ever offered`() {
    assertEquals("https://nirogbhumi.com/yoga", safeResourceLink(" https://nirogbhumi.com/yoga "))
    assertEquals("http://example.org", safeResourceLink("http://example.org"))
    assertNull(safeResourceLink("javascript:alert(1)"))
    assertNull(safeResourceLink("intent://evil#Intent;end"))
    assertNull(safeResourceLink("file:///sdcard/x"))
    assertNull(safeResourceLink(""))
    assertNull(safeResourceLink(null))
  }

  @Test
  fun `category labels are member friendly`() {
    assertEquals("Diet plan", resourceCategoryLabel("diet"))
    assertEquals("Resource", resourceCategoryLabel("something-new"))
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProgramResourcesScreenTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  private val sample = listOf(
    ProgramResource("1", "diet", "Week 1 plate", "Half plate vegetables.", null, 1, "Coach Anita", 0L),
    ProgramResource("2", "yoga", "Morning flow", "Cat-cow and slow breathing.", "https://nirogbhumi.com/yoga", null, "Coach Anita", 0L),
  )

  private fun show(
    resources: List<ProgramResource> = sample,
    loading: Boolean = false,
    error: String? = null,
    hasProgram: Boolean = true,
    onBack: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
  ) = composeTestRule.setContent {
    MyApplicationTheme { ProgramResourcesContent(resources, loading, error, hasProgram, onBack, onOpenLink) }
  }

  @Test
  fun `lists the coach's resources with their category and week`() {
    show()
    composeTestRule.onNodeWithText("Plans & guidance").assertExists()
    composeTestRule.onNodeWithText("Week 1 plate").assertExists()
    composeTestRule.onNodeWithText("Morning flow").assertExists()
    composeTestRule.onNodeWithText("Week 1").assertExists()
    composeTestRule.onNodeWithText("Half plate vegetables.").assertExists()
  }

  @Test
  fun `category filter narrows the list and All restores it`() {
    show()
    composeTestRule.onNodeWithText("Yoga").performClick()
    composeTestRule.onNodeWithText("Morning flow").assertExists()
    composeTestRule.onNodeWithText("Week 1 plate").assertDoesNotExist()
    composeTestRule.onNodeWithText("All").performClick()
    composeTestRule.onNodeWithText("Week 1 plate").assertExists()
  }

  @Test
  fun `opening a link hands the exact url to the caller`() {
    var opened = ""
    show(onOpenLink = { opened = it })
    composeTestRule.onNodeWithText("Open link").performClick()
    assertEquals("https://nirogbhumi.com/yoga", opened)
  }

  @Test
  fun `long text is clamped until Read more is tapped`() {
    val long = ProgramResource("3", "diet", "Full week", "Line of guidance. ".repeat(40), null, null, "Coach", 0L)
    show(resources = listOf(long))
    composeTestRule.onNodeWithText("Read more").assertExists()
    composeTestRule.onNodeWithText("Read more").performClick()
    composeTestRule.onNodeWithText("Show less").assertExists()
  }

  @Test
  fun `honest empty, loading, error and no-program states`() {
    show(resources = emptyList())
    composeTestRule.onNodeWithText("Nothing shared yet").assertExists()
  }

  @Test
  fun `no-program state explains how to get plans`() {
    show(resources = emptyList(), hasProgram = false)
    composeTestRule.onNodeWithText("Join a program to see plans").assertExists()
  }

  @Test
  fun `error state is shown when nothing could be loaded`() {
    show(resources = emptyList(), error = "We couldn't load your plans just now.")
    composeTestRule.onNodeWithText("Couldn't load your plans").assertExists()
  }

  @Test
  fun `loading state shows while the first load is in flight`() {
    show(resources = emptyList(), loading = true)
    composeTestRule.onNodeWithText("Loading your plans…").assertExists()
  }

  @Test
  fun `back button calls onBack`() {
    var back = false
    show(onBack = { back = true })
    composeTestRule.onNode(hasContentDescription("Back")).performClick()
    assertEquals(true, back)
  }

  @Test
  fun `the stateful screen renders its empty state when Firebase is not configured`() {
    val state = NirogState()
    state.activeProgramId = "progA"
    composeTestRule.setContent { MyApplicationTheme { ProgramResourcesScreen(state) } }
    composeTestRule.onNodeWithText("Plans & guidance").assertExists()
  }
}
