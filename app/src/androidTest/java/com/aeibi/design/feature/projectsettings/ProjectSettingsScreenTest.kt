package com.aeibi.design.feature.projectsettings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.aeibi.design.data.projects.ProjectRepository
import com.aeibi.design.data.sessions.SessionDao
import com.aeibi.design.data.sessions.SessionEntity
import com.aeibi.design.data.sessions.SessionRepository
import com.aeibi.design.feature.projects.ProjectsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectSettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeSessionDao : SessionDao {
        override fun observeSessions(projectId: String): Flow<List<SessionEntity>> = flowOf(emptyList())

        override suspend fun getSession(sessionId: String): SessionEntity? = null

        override suspend fun upsertSession(session: SessionEntity) = Unit

        override suspend fun renameSession(sessionId: String, title: String, updatedAt: Long): Int = 0

        override suspend fun touchSession(sessionId: String, updatedAt: Long): Int = 0

        override suspend fun deleteSession(sessionId: String): Int = 0

        override suspend fun deleteSessionsForProject(projectId: String): Int = 0
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun settingsScreen_showsIconPicker() {
        val context = composeTestRule.activity.applicationContext
        val root = context.getDir("projects-test", 0)
        val repository = ProjectRepository(root, context.contentResolver, dispatcher)
        val viewModel = ProjectsViewModel(repository, SessionRepository(FakeSessionDao()))

        composeTestRule.setContent {
            ProjectSettingsScreen(projectId = "任意项目", viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag("pick_project_icon_button").assertIsDisplayed()
    }
}
