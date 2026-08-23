package com.aeibi.design.feature.projects

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [ProjectsScreen]. */
class ProjectsScreenTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent { ProjectsScreen() }
    }

    @Test
    fun projectItems_exist() {
        listOf("日常发芽", "周末去哪", "专注计时器").forEach {
            composeTestRule.onNodeWithText(it).assertExists()
        }
    }
}
