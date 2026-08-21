package com.aeibi.design.feature.projects

import com.aeibi.design.data.project.InMemoryProjectRepository
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class ProjectsViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun defaultProjects_areLoaded() = runTest {
    val viewModel = ProjectsViewModel(InMemoryProjectRepository())
    mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    val projects = viewModel.uiState.value.projects

    assertTrue(projects.any { it.name == "日常发芽" })
    assertTrue(projects.any { it.name == "周末去哪" })
    assertTrue(projects.any { it.name == "专注计时器" })
  }

  @Test
  fun createProject_updatesListWithNameAndDescription() = runTest {
    val viewModel = ProjectsViewModel(InMemoryProjectRepository())
    mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    viewModel.createProject("我的首页", "一个简洁的移动端首页", null)
    mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    val created = viewModel.uiState.value.projects.first()
    assertEquals("我的首页", created.name)
    assertEquals("一个简洁的移动端首页", created.description)
  }

  @Test
  fun blankName_doesNotCreateProject() = runTest {
    val viewModel = ProjectsViewModel(InMemoryProjectRepository())
    testScheduler.advanceUntilIdle()
    val initialCount = viewModel.uiState.value.projects.size

    viewModel.createProject("   ", "描述", null)
    testScheduler.advanceUntilIdle()

    assertEquals(initialCount, viewModel.uiState.value.projects.size)
  }
}

private class MainDispatcherRule : TestRule {
  val dispatcher = StandardTestDispatcher()

  override fun apply(base: Statement, description: Description): Statement =
    object : Statement() {
      override fun evaluate() {
        Dispatchers.setMain(dispatcher)
        try {
          base.evaluate()
        } finally {
          Dispatchers.resetMain()
        }
      }
    }
}
