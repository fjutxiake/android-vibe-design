package com.aeibi.design.feature.preview

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.aeibi.design.R
import com.aeibi.design.feature.workspace.PreviewStatus
import com.aeibi.design.feature.workspace.PreviewUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProjectPreviewScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun stoppedStateEnablesOnlyStart() {
        composeTestRule.setContent { ProjectPreviewScreen(PreviewUiState()) }

        composeTestRule.onNodeWithContentDescription(text(R.string.preview_cd_refresh)).assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription(text(R.string.preview_cd_start)).assertIsEnabled()
        composeTestRule.onNodeWithContentDescription(text(R.string.preview_cd_fullscreen)).assertIsNotEnabled()
    }

    @Test
    fun transitioningStateDisablesBackendAction() {
        composeTestRule.setContent {
            ProjectPreviewScreen(PreviewUiState(status = PreviewStatus.STARTING))
        }

        composeTestRule.onNodeWithContentDescription(text(R.string.preview_cd_start)).assertIsNotEnabled()
    }

    @Test
    fun runningActionsInvokeCallbacks() {
        val actions = mutableListOf<String>()
        composeTestRule.setContent {
            ProjectPreviewScreen(
                state = PreviewUiState(status = PreviewStatus.RUNNING),
                onRefreshClick = { actions += "refresh" },
                onToggleBackendClick = { actions += "stop" },
                onFullscreenClick = { actions += "fullscreen" }
            )
        }

        composeTestRule.onNodeWithContentDescription(text(R.string.preview_cd_refresh)).performClick()
        composeTestRule.onNodeWithContentDescription(text(R.string.preview_cd_stop)).performClick()
        composeTestRule.onNodeWithContentDescription(text(R.string.preview_cd_fullscreen)).performClick()
        assertEquals(listOf("refresh", "stop", "fullscreen"), actions)
    }

    private fun text(id: Int): String = composeTestRule.activity.getString(id)
}
