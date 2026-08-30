package com.aeibi.design.feature.projects

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aeibi.design.data.projects.Project
import com.aeibi.design.data.projects.ProjectRepository
import com.aeibi.design.data.sessions.SessionDao
import com.aeibi.design.data.sessions.SessionEntity
import com.aeibi.design.data.sessions.SessionRepository
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private class FakeSessionDao(private val onDeleteForProject: () -> Int = { 0 }) : SessionDao {
        override fun observeSessions(projectId: String): Flow<List<SessionEntity>> = flowOf(emptyList())

        override suspend fun getSession(sessionId: String): SessionEntity? = null

        override suspend fun upsertSession(session: SessionEntity) = Unit

        override suspend fun renameSession(sessionId: String, title: String, updatedAt: Long): Int = 0

        override suspend fun touchSession(sessionId: String, updatedAt: Long): Int = 0

        override suspend fun deleteSession(sessionId: String): Int = 0

        override suspend fun deleteSessionsForProject(projectId: String): Int = onDeleteForProject()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(root: File, dao: SessionDao = FakeSessionDao()): ProjectsViewModel {
        val repository = ProjectRepository(root, context.contentResolver, context.assets, dispatcher)
        return ProjectsViewModel(repository, SessionRepository(dao))
    }

    @Test
    fun createProject_whenRepositoryFails_reportsFailure() = runTest {
        // 项目根目录是个普通文件,建目录必然失败。
        val root = tmp.newFile()
        var result: Result<Project>? = null

        viewModel(root).createProject("新项目", "", null) { result = it }

        assertTrue("应当上报失败,实际为 $result", result?.isFailure == true)
    }

    @Test
    fun createProject_whenSuccessful_reportsSuccess() = runTest {
        var result: Result<Project>? = null

        viewModel(tmp.newFolder()).createProject("新项目", "", null) { result = it }

        assertTrue("应当上报成功,实际为 $result", result?.isSuccess == true)
        assertTrue(result?.getOrNull()?.isInitialized == false)
    }

    @Test
    fun markInitialized_whenSuccessful_updatesProject() = runTest {
        val root = tmp.newFolder()
        val repository = ProjectRepository(root, context.contentResolver, context.assets, dispatcher)
        val created = repository.createProject("New project", "", null)
        var result: Result<Unit>? = null

        ProjectsViewModel(repository, SessionRepository(FakeSessionDao()))
            .markInitialized(created.id) { result = it }

        assertTrue(result?.isSuccess == true)
        assertTrue(repository.getProject(created.id)?.isInitialized == true)
    }

    @Test
    fun updateProject_whenProjectMissing_reportsFailure() = runTest {
        var result: Result<Unit>? = null

        viewModel(tmp.newFolder()).updateProject("不存在的项目", "新名", "新描述", null) { result = it }

        assertTrue("应当上报失败,实际为 $result", result?.isFailure == true)
    }

    @Test
    fun deleteProject_whenDirectoryCannotBeRemoved_reportsFailure() = runTest {
        val root = tmp.newFolder()
        val repository = ProjectRepository(root, context.contentResolver, context.assets, dispatcher)
        val created = repository.createProject("删不掉", "", null)
        val dir = File(root, created.id)
        val locked = File(dir, "locked.bin").apply { writeText("x") }
        val handle = FileInputStream(locked)
        dir.setWritable(false)
        var result: Result<Unit>? = null

        try {
            ProjectsViewModel(repository, SessionRepository(FakeSessionDao()))
                .deleteProject(created.id) { result = it }

            assertTrue("应当上报失败,实际为 $result", result?.isFailure == true)
        } finally {
            handle.close()
            dir.setWritable(true)
        }
    }

    @Test
    fun deleteProject_whenSuccessful_reportsSuccess() = runTest {
        val root = tmp.newFolder()
        val repository = ProjectRepository(root, context.contentResolver, context.assets, dispatcher)
        val created = repository.createProject("待删", "", null)
        var result: Result<Unit>? = null

        ProjectsViewModel(repository, SessionRepository(FakeSessionDao()))
            .deleteProject(created.id) { result = it }

        assertTrue("应当上报成功,实际为 $result", result?.isSuccess == true)
        assertTrue(!File(root, created.id).exists())
    }

    @Test
    fun deleteProject_whenSessionCleanupFails_stillDeletesProjectAndReportsSuccess() = runTest {
        val root = tmp.newFolder()
        val repository = ProjectRepository(root, context.contentResolver, context.assets, dispatcher)
        val created = repository.createProject("待删", "", null)
        val failingDao = FakeSessionDao { throw IllegalStateException("数据库不可用") }
        var result: Result<Unit>? = null

        ProjectsViewModel(repository, SessionRepository(failingDao))
            .deleteProject(created.id) { result = it }

        // 会话清理是尽力而为:清理失败不应阻止项目删除,留下孤儿会话是可接受的。
        assertTrue("应当上报成功,实际为 $result", result?.isSuccess == true)
        assertTrue(!File(root, created.id).exists())
        assertEquals(emptyList<Any>(), repository.projects.value)
    }
}
