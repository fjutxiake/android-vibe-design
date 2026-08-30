package com.aeibi.design.feature.projects

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import com.aeibi.design.R
import com.aeibi.design.data.projects.Project
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ProjectsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val sample = listOf(
        Project("1", "日常发芽", "不焦虑的日常习惯记录", null, 1L, 1L),
        Project("2", "周末去哪", "根据心情生成短途路线", null, 1L, 2L)
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

    @Test
    fun longPressingItem_opensEditSheetAndSavesChanges() {
        var updated: List<String?>? = null
        composeTestRule.setContent {
            ProjectsScreen(
                projects = sample,
                onUpdateProject = { id, name, description, iconUri, onResult ->
                    updated = listOf(id, name, description, iconUri)
                    onResult(Result.success(Unit))
                }
            )
        }

        composeTestRule
            .onNodeWithText("日常发芽")
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.projects_edit_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_project_name_input").performTextClearance()
        composeTestRule.onNodeWithTag("edit_project_name_input").performTextInput("新名称")
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.save))
            .performClick()

        assertEquals(listOf("1", "新名称", "不焦虑的日常习惯记录", null), updated)
    }

    @Test
    fun deletingFromEditSheet_invokesOnDeleteProject() {
        var deletedId: String? = null
        composeTestRule.setContent {
            ProjectsScreen(
                projects = sample,
                onDeleteProject = { id, onResult ->
                    deletedId = id
                    onResult(Result.success(Unit))
                }
            )
        }

        composeTestRule
            .onNodeWithText("日常发芽")
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeTestRule.onNodeWithTag("delete_project_button").performClick()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.delete_project_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_delete_project_button").performClick()

        assertEquals("1", deletedId)
    }

    @Test
    fun createSheet_invokesOnCreateProject() {
        var created: Triple<String, String, String?>? = null
        composeTestRule.setContent {
            ProjectsScreen(projects = emptyList(), onCreateProject = { n, d, i, onResult ->
                created = Triple(n, d, i)
                onResult(Result.success(sample.first()))
            })
        }

        composeTestRule.onNodeWithTag("new_project_button").performClick()
        composeTestRule.onNodeWithTag("project_name_input").performTextInput("新项目")
        composeTestRule.onNodeWithTag("project_description_input").performTextInput("描述")
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.projects_create_button))
            .performClick()

        assertEquals("新项目", created?.first)
        assertEquals("描述", created?.second)
        assertNull(created?.third)
    }

    @Test
    fun createSheet_whenCreateFails_staysOpenAndShowsError() {
        composeTestRule.setContent {
            ProjectsScreen(projects = emptyList(), onCreateProject = { _, _, _, onResult ->
                onResult(Result.failure(IOException("磁盘写入失败")))
            })
        }

        composeTestRule.onNodeWithTag("new_project_button").performClick()
        composeTestRule.onNodeWithTag("project_name_input").performTextInput("新项目")
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.projects_create_button))
            .performClick()

        // 失败时面板必须留着,已填的名称不能丢,并且要给出错误提示。
        composeTestRule.onNodeWithTag("create_project_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("project_name_input").assertIsDisplayed()
    }

    @Test
    fun createSheet_whenCreateSucceeds_closesSheet() {
        composeTestRule.setContent {
            ProjectsScreen(projects = emptyList(), onCreateProject = { _, _, _, onResult ->
                onResult(Result.success(sample.first()))
            })
        }

        composeTestRule.onNodeWithTag("new_project_button").performClick()
        composeTestRule.onNodeWithTag("project_name_input").performTextInput("新项目")
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.projects_create_button))
            .performClick()

        composeTestRule.onNodeWithTag("project_name_input").assertDoesNotExist()
    }

    @Test
    fun createSheet_whileCreating_disablesCancel() {
        composeTestRule.setContent {
            ProjectsScreen(
                projects = emptyList(),
                onCreateProject = { _, _, _, _ -> }
            )
        }

        composeTestRule.onNodeWithTag("new_project_button").performClick()
        composeTestRule.onNodeWithTag("project_name_input").performTextInput("New project")
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.projects_create_button))
            .performClick()

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.cancel))
            .assertIsNotEnabled()
        pressBack()
        composeTestRule.onNodeWithTag("project_name_input").assertIsDisplayed()
    }
}
