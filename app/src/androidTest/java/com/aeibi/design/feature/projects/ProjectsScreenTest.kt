package com.aeibi.design.feature.projects

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.aeibi.design.data.projects.Project
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProjectsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val sample = listOf(
        Project("1", "日常发芽", "不焦虑的日常习惯记录", null, 1L, 1L),
        Project("2", "周末去哪", "根据心情生成短途路线", null, 1L, 2L),
    )

    @Test
    fun projectItems_render() {
        composeTestRule.setContent { ProjectsScreen(projects = sample) }

        composeTestRule.onNodeWithText("日常发芽").assertIsDisplayed()
        composeTestRule.onNodeWithText("周末去哪").assertIsDisplayed()
    }

    @Test
    fun emptyState_shownWhenNoProjects() {
        composeTestRule.setContent { ProjectsScreen(projects = emptyList()) }

        composeTestRule.onNodeWithTag("empty_projects").assertIsDisplayed()
    }

    @Test
    fun clickingItem_invokesOnProjectClick() {
        var clicked: String? = null
        composeTestRule.setContent {
            ProjectsScreen(projects = sample, onProjectClick = { clicked = it })
        }

        composeTestRule.onNodeWithText("日常发芽").performClick()

        assertEquals("1", clicked)
    }
}
