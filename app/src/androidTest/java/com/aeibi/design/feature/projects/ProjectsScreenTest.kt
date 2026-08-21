package com.aeibi.design.feature.projects

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsNotEnabled
import com.aeibi.design.data.project.InMemoryProjectRepository
import com.aeibi.design.theme.VibeDesignTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [ProjectsScreen]. */
class ProjectsScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent {
      VibeDesignTheme(dynamicColor = false) {
        ProjectsRoute(projectRepository = InMemoryProjectRepository())
      }
    }
  }

  @Test
  fun projectItems_exist() {
    listOf("日常发芽", "周末去哪", "专注计时器").forEach {
      composeTestRule.onNodeWithText(it).assertExists()
    }
  }

  @Test
  fun createProject_addsProjectToList() {
    composeTestRule.onNodeWithTag("new_project_button").performClick()
    composeTestRule.onNodeWithTag("create_project_button").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("project_name_input").performTextInput("我的首页")
    composeTestRule
      .onNodeWithTag("project_description_input")
      .performTextInput("一个简洁的移动端首页")
    composeTestRule.onNodeWithTag("create_project_button").performClick()

    composeTestRule.onNodeWithText("我的首页").assertExists()
    composeTestRule.onNodeWithText("日常发芽").assertExists()
  }
}
