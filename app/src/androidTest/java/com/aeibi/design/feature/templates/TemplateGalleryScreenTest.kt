package com.aeibi.design.feature.templates

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.espresso.Espresso.pressBack
import com.aeibi.design.data.templates.Template
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TemplateGalleryScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val templates = listOf(
        Template(
            id = "focus-planner",
            category = "productivity",
            name = "Focus Planner",
            description = "Daily tasks and a focus timer.",
            coverAssetPath = "workspace-templates/focus-planner/previews/cover.webp",
            previewAssetPaths = emptyList(),
            readmeAssetPath = "workspace-templates/focus-planner/README.md",
            workspaceAssetPath = "workspace-templates/focus-planner/workspace"
        ),
        Template(
            id = "tabbed-workspace",
            category = "productivity",
            name = "Tabbed Workspace",
            description = "Home, activity, and profile pages.",
            coverAssetPath = "workspace-templates/tabbed-workspace/previews/cover.webp",
            previewAssetPaths = listOf(
                "workspace-templates/tabbed-workspace/previews/activity.webp",
                "workspace-templates/tabbed-workspace/previews/profile.webp"
            ),
            readmeAssetPath = "workspace-templates/tabbed-workspace/README.md",
            workspaceAssetPath = "workspace-templates/tabbed-workspace/workspace"
        )
    )

    @Test
    fun gallery_showsCategoryNameAndDescription() {
        setGalleryContent()

        composeTestRule.onAllNodesWithText("productivity", useUnmergedTree = true).assertCountEquals(2)
        composeTestRule.onNodeWithText("Focus Planner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily tasks and a focus timer.").assertIsDisplayed()
    }

    @Test
    fun clickingCard_opensDetail() {
        var state by mutableStateOf(loadedState())
        setGalleryContent(
            state = { state },
            onTemplateClick = { state = state.copy(selectedTemplate = it, readme = "# README") }
        )

        composeTestRule.onNodeWithTag("template_card_focus-planner").performClick()

        composeTestRule.onNodeWithTag("template_detail_focus-planner").assertExists()
    }

    @Test
    fun pager_swipesToNextImage() {
        setGalleryContent(
            initialState = loadedState().copy(selectedTemplate = templates[1], readme = "README")
        )

        composeTestRule.onNodeWithTag("template_preview_pager").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("template_pager_page_2").assertExists()
    }

    @Test
    fun useTemplateButton_invokesCallback() {
        var clicked = false
        setGalleryContent(
            initialState = loadedState().copy(selectedTemplate = templates[0], readme = "README"),
            onUseTemplateClick = { clicked = true }
        )

        composeTestRule.onNodeWithTag("use_template_button").performClick()

        assertEquals(true, clicked)
    }

    @Test
    fun applyingTemplate_disablesButtonAndShowsProgress() {
        setGalleryContent(
            initialState = loadedState().copy(
                selectedTemplate = templates[0],
                readme = "README",
                isApplyingTemplate = true
            )
        )

        composeTestRule.onNodeWithTag("use_template_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("template_apply_loading").assertIsDisplayed()
    }

    @Test
    fun applyFailure_showsError() {
        setGalleryContent(
            initialState = loadedState().copy(
                selectedTemplate = templates[0],
                readme = "README",
                applyFailed = true
            )
        )

        composeTestRule.onNodeWithTag("template_apply_failed").assertIsDisplayed()
    }

    @Test
    fun systemBack_closesDetailBeforeLeavingGallery() {
        var state by mutableStateOf(
            loadedState().copy(selectedTemplate = templates[0], readme = "README")
        )
        setGalleryContent(
            state = { state },
            onCloseTemplate = { state = state.copy(selectedTemplate = null, readme = null) }
        )

        pressBack()

        composeTestRule.onNodeWithTag("template_detail_focus-planner").assertDoesNotExist()
        composeTestRule.onNodeWithTag("template_card_focus-planner").assertExists()
    }

    private fun setGalleryContent(
        initialState: TemplateGalleryUiState = loadedState(),
        state: (() -> TemplateGalleryUiState)? = null,
        onTemplateClick: (Template) -> Unit = {},
        onCloseTemplate: () -> Unit = {},
        onUseTemplateClick: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            TemplateGalleryContent(
                uiState = state?.invoke() ?: initialState,
                onBackClick = {},
                onRetry = {},
                onTemplateClick = onTemplateClick,
                onCloseTemplate = onCloseTemplate,
                onUseTemplateClick = onUseTemplateClick
            )
        }
    }

    private fun loadedState() = TemplateGalleryUiState(
        templates = templates,
        isLoading = false
    )
}
