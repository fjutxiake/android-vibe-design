package com.aeibi.design.feature.preview

import android.webkit.ConsoleMessage
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.aeibi.design.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConsoleScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyStateShowsMessageAndDisablesClear() {
        composeTestRule.setContent { ConsoleScreen(messages = emptyList()) }

        composeTestRule.onNodeWithText(text(R.string.preview_console_empty)).assertExists()
        composeTestRule
            .onNodeWithContentDescription(text(R.string.preview_console_cd_clear))
            .assertIsNotEnabled()
    }

    @Test
    fun messagesAreShownAndClearInvokesCallback() {
        var clearClicks = 0
        composeTestRule.setContent {
            ConsoleScreen(
                messages = listOf(consoleMessage("hello console")),
                onClearClick = { clearClicks += 1 }
            )
        }

        composeTestRule.onNodeWithText("hello console").assertTextEquals("hello console")
        composeTestRule
            .onNodeWithContentDescription(text(R.string.preview_console_cd_clear))
            .performClick()
        assertEquals(1, clearClicks)
    }

    private fun consoleMessage(message: String) = ConsoleMessage(
        message,
        "",
        1,
        ConsoleMessage.MessageLevel.LOG
    )

    private fun text(id: Int): String = composeTestRule.activity.getString(id)
}
