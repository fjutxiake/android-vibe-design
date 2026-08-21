package com.aeibi.design.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aeibi.design.data.project.ProjectRepositoryProvider
import com.aeibi.design.data.project.InMemoryProjectRepository
import com.aeibi.design.domain.repository.ProjectRepository
import com.aeibi.design.domain.model.Project
import com.aeibi.design.theme.VibeDesignTheme
import com.aeibi.design.theme.spacing

@Composable
fun ProjectsRoute(
  modifier: Modifier = Modifier,
  isDarkTheme: Boolean = false,
  onThemeToggle: () -> Unit = {},
  onSettingsClick: () -> Unit = {},
  onProjectClick: (String) -> Unit = {},
  projectRepository: ProjectRepository = ProjectRepositoryProvider.instance,
) {
  val viewModel: ProjectsViewModel =
    viewModel(factory = ProjectsViewModelFactory(projectRepository))
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  ProjectsScreen(
    modifier = modifier,
    projects = uiState.projects,
    isDarkTheme = isDarkTheme,
    onThemeToggle = onThemeToggle,
    onSettingsClick = onSettingsClick,
    onProjectClick = onProjectClick,
    onCreateProject = viewModel::createProject,
  )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProjectsScreen(
  projects: List<Project>,
  modifier: Modifier = Modifier,
  isDarkTheme: Boolean = false,
  onThemeToggle: () -> Unit = {},
  onSettingsClick: () -> Unit = {},
  onProjectClick: (String) -> Unit = {},
  onCreateProject: (String, String, String?) -> Unit = { _, _, _ -> },
) {
  val spacing = MaterialTheme.spacing
  var showNewProjectSheet by rememberSaveable { mutableStateOf(false) }
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text("Aeibi") },
        actions = {
          IconButton(onClick = onThemeToggle) {
            Icon(
              imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
              contentDescription = if (isDarkTheme) "切换到浅色模式" else "切换到深色模式",
            )
          }
          IconButton(
            onClick = { showNewProjectSheet = true },
            modifier = Modifier.testTag("new_project_button"),
          ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "新建项目")
          }
          IconButton(onClick = onSettingsClick) {
            Icon(imageVector = Icons.Filled.Settings, contentDescription = "设置")
          }
        },
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding(),
      contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.lg),
      verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
      items(projects, key = { it.id }) { project ->
        ProjectListItem(
          name = project.name,
          description = project.description,
          updatedAt = formatUpdatedAt(project.updatedAt),
          iconUri = project.iconUri,
          onClick = { onProjectClick(project.id) },
        )
      }
    }
  }

  if (showNewProjectSheet) {
    NewProjectBottomSheet(
      onDismiss = { showNewProjectSheet = false },
      onCreateProject = onCreateProject,
    )
  }
}

@Preview(showBackground = true)
@Composable
fun ProjectsScreenPreview() {
  VibeDesignTheme(dynamicColor = false) {
    ProjectsScreen(projects = InMemoryProjectRepository.defaultProjects())
  }
}

@Preview(showBackground = true, widthDp = 340, heightDp = 700)
@Composable
fun ProjectsScreenPortraitPreview() {
  VibeDesignTheme(dynamicColor = false) {
    ProjectsScreen(projects = InMemoryProjectRepository.defaultProjects())
  }
}

private fun formatUpdatedAt(updatedAt: Long): String {
  val age = System.currentTimeMillis() - updatedAt
  return when {
    age < 60 * 60 * 1000L -> "刚刚修改"
    age < 2 * 24 * 60 * 60 * 1000L -> "昨天修改"
    else -> java.text.SimpleDateFormat("M月d日修改", java.util.Locale.CHINA).format(updatedAt)
  }
}
