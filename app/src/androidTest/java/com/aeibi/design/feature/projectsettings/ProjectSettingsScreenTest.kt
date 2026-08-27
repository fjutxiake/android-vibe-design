package com.aeibi.design.feature.projectsettings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.aeibi.design.R
import org.junit.Rule
import org.junit.Test

class ProjectSettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsScreen_showsPlaceholder() {
        composeTestRule.setContent { ProjectSettingsScreen(projectId = "任意项目") }

        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(
                    R.string.project_settings_placeholder,
                    "任意项目"
                )
            )
            .assertIsDisplayed()
    }
}
