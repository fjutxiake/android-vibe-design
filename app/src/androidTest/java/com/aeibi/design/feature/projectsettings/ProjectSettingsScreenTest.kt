package com.aeibi.design.feature.projectsettings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ProjectSettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsScreen_showsPlaceholder() {
        composeTestRule.setContent { ProjectSettingsScreen(projectId = "任意项目") }

        composeTestRule.onNodeWithText("项目 任意项目 的设置区域").assertIsDisplayed()
    }
}
