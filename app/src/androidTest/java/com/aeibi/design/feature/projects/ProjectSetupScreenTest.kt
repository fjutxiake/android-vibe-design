package com.aeibi.design.feature.projects

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import com.aeibi.design.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProjectSetupScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun actionsInvokeCallbacks() {
        var browsedTemplates = false
        var startedBlank = false
        composeTestRule.setContent {
            ProjectSetupScreen(
                onBackClick = {},
                onBrowseTemplatesClick = { browsedTemplates = true },
                onStartBlankClick = { onResult ->
                    startedBlank = true
                    onResult(Result.success(Unit))
                }
            )
        }

        composeTestRule.onNodeWithTag("browse_templates").performClick()
        composeTestRule.onNodeWithTag("start_blank").performClick()

        assertTrue(browsedTemplates)
        assertTrue(startedBlank)
    }

    @Test
    fun blankStartFailureShowsError() {
        composeTestRule.setContent {
            ProjectSetupScreen(
                onBackClick = {},
                onBrowseTemplatesClick = {},
                onStartBlankClick = { onResult ->
                    onResult(Result.failure(IllegalStateException("Initialization failed")))
                }
            )
        }

        composeTestRule.onNodeWithTag("start_blank").performClick()

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.project_setup_start_failed))
            .assertIsDisplayed()
    }

    @Test
    fun blankStartInProgress_disablesBackNavigation() {
        composeTestRule.setContent {
            ProjectSetupScreen(
                onBackClick = {},
                onBrowseTemplatesClick = {},
                onStartBlankClick = {}
            )
        }

        composeTestRule.onNodeWithTag("start_blank").performClick()

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.back))
            .assertIsNotEnabled()
        pressBack()
        composeTestRule.onNodeWithTag("start_blank").assertIsDisplayed()
    }
}
