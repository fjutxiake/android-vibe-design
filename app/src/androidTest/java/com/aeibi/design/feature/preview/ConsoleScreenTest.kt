package com.aeibi.design.feature.preview

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.aeibi.design.R
import com.aeibi.design.data.runtimelogs.RuntimeLogEntry
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConsoleScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyStateShowsMessageAndDisablesClear() {
        composeTestRule.setContent { ConsoleScreen(logs = flowOf(emptyList())) }

        composeTestRule.onNodeWithText(text(R.string.preview_console_empty)).assertExists()
        composeTestRule
            .onNodeWithContentDescription(text(R.string.preview_console_cd_clear))
            .assertIsNotEnabled()
    }

    @Test
    fun logsAreShownAndClearInvokesCallback() {
        var clearClicks = 0
        composeTestRule.setContent {
            ConsoleScreen(
                logs = flowOf(listOf(logEntry("hello console"))),
                onClearClick = { clearClicks += 1 }
            )
        }

        composeTestRule.onNodeWithText("hello console").assertTextEquals("hello console")
        composeTestRule
            .onNodeWithContentDescription(text(R.string.preview_console_cd_clear))
            .performClick()
        assertEquals(1, clearClicks)
    }

    @Test
    fun addToChatSendsOnlySelectedEntries() {
        var added: List<RuntimeLogEntry>? = null
        composeTestRule.setContent {
            ConsoleScreen(
                logs = flowOf(
                    listOf(
                        logEntry("boom", level = "ERROR", source = "app.js:3"),
                        logEntry("noise", level = "LOG", source = "app.js:4")
                    )
                ),
                onAddToChatClick = { added = it }
            )
        }

        // 选中第一条（ERROR），顶栏出现「添加到聊天（1）」
        composeTestRule.onNodeWithText("boom").performClick()
        composeTestRule.onNodeWithText(text(R.string.preview_console_add_selected, 1)).performClick()

        val selected = requireNotNull(added)
        assertEquals(1, selected.size)
        assertEquals("boom", selected.single().message)
    }

    @Test
    fun multipleSelectionIsBatched() {
        var added: List<RuntimeLogEntry>? = null
        composeTestRule.setContent {
            ConsoleScreen(
                logs = flowOf(
                    listOf(
                        logEntry("error one", level = "ERROR"),
                        logEntry("error two", level = "ERROR")
                    )
                ),
                onAddToChatClick = { added = it }
            )
        }

        composeTestRule.onNodeWithText("error one").performClick()
        composeTestRule.onNodeWithText("error two").performClick()
        composeTestRule.onNodeWithText(text(R.string.preview_console_add_selected, 2)).performClick()

        assertEquals(2, requireNotNull(added).size)
    }

    private fun logEntry(message: String, level: String = "LOG", source: String = "") =
        RuntimeLogEntry(level = level, message = message, source = source, timestamp = 1L)

    private fun text(id: Int, vararg args: Any): String = composeTestRule.activity.getString(id, *args)
}
