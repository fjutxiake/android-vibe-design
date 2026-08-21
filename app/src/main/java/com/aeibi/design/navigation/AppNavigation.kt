package com.aeibi.design.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.aeibi.design.data.settings.AppSettings
import com.aeibi.design.feature.build.ProjectBuildScreen
import com.aeibi.design.feature.preview.ProjectPreviewScreen
import com.aeibi.design.data.project.ProjectRepositoryProvider
import com.aeibi.design.feature.projects.ProjectsRoute
import com.aeibi.design.feature.projectsettings.ProjectSettingsScreen
import com.aeibi.design.feature.settings.AppSettingsEvent
import com.aeibi.design.feature.settings.SettingsScreen
import com.aeibi.design.feature.versions.ProjectVersionsScreen
import com.aeibi.design.feature.workspace.ProjectWorkspaceScreen

private const val DefaultSessionId = "new-session"

@Composable
fun AppNavigation(
  settings: AppSettings,
  onSettingsEvent: (AppSettingsEvent) -> Unit,
) {
  val backStack = rememberNavBackStack(ProjectPicker)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<ProjectChat> { route ->
          ProjectWorkspaceScreen(
            projectId = route.projectId,
            sessionId = route.sessionId,
            modifier = Modifier.fillMaxSize(),
            onProjectPickerClick = { backStack.removeLastOrNull() },
            onSessionSelected = { sessionId ->
              backStack.removeLastOrNull()
              backStack.add(route.copy(sessionId = sessionId))
            },
            onPreviewClick = { backStack.add(ProjectPreview(route.projectId)) },
            onBuildClick = { backStack.add(ProjectBuild(route.projectId)) },
            onVersionsClick = { backStack.add(ProjectVersions(route.projectId)) },
            onProjectSettingsClick = { backStack.add(ProjectSettings(route.projectId)) },
            onAppSettingsClick = { backStack.add(ApplicationSettings) },
          )
        }
        entry<ProjectPicker> {
          ProjectsRoute(
            modifier = Modifier.fillMaxSize(),
            isDarkTheme = settings.isDarkTheme,
            onThemeToggle = { onSettingsEvent(AppSettingsEvent.ToggleThemeMode) },
            onSettingsClick = { backStack.add(ApplicationSettings) },
            onProjectClick = { projectId ->
              backStack.add(ProjectChat(projectId, DefaultSessionId))
            },
            projectRepository = ProjectRepositoryProvider.instance,
          )
        }
        entry<ProjectPreview> { route ->
          ProjectPreviewScreen(
            projectId = route.projectId,
            modifier = Modifier.fillMaxSize(),
            onBackClick = { backStack.removeLastOrNull() },
          )
        }
        entry<ProjectBuild> { route ->
          ProjectBuildScreen(
            projectId = route.projectId,
            modifier = Modifier.fillMaxSize(),
            onBackClick = { backStack.removeLastOrNull() },
          )
        }
        entry<ProjectVersions> { route ->
          ProjectVersionsScreen(
            projectId = route.projectId,
            modifier = Modifier.fillMaxSize(),
            onBackClick = { backStack.removeLastOrNull() },
          )
        }
        entry<ProjectSettings> { route ->
          ProjectSettingsScreen(
            projectId = route.projectId,
            modifier = Modifier.fillMaxSize(),
            onBackClick = { backStack.removeLastOrNull() },
          )
        }
        entry<ApplicationSettings> {
          SettingsScreen(
            modifier = Modifier.fillMaxSize(),
            settings = settings,
            onSettingsEvent = onSettingsEvent,
            onBackClick = { backStack.removeLastOrNull() },
          )
        }
      },
  )
}
