package com.aeibi.design.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.aeibi.design.feature.apktest.TestApkEditorScreen
import com.aeibi.design.feature.build.ProjectBuildScreen
import com.aeibi.design.feature.preview.ProjectPreviewScreen
import com.aeibi.design.feature.projects.ProjectsScreen
import com.aeibi.design.feature.projects.ProjectsViewModel
import com.aeibi.design.feature.projectsettings.ProjectSettingsScreen
import com.aeibi.design.feature.settings.SettingsScreen
import com.aeibi.design.feature.settings.ai.AiProvidersScreen
import com.aeibi.design.feature.settings.language.LanguageSettingsScreen
import com.aeibi.design.feature.versions.ProjectVersionsScreen
import com.aeibi.design.feature.workspace.ProjectWorkspaceScreen

@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(ProjectPicker)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider =
        entryProvider {
            entry<ProjectChat> { route ->
                ProjectWorkspaceScreen(
                    projectId = route.projectId,
                    modifier = Modifier.fillMaxSize(),
                    onProjectPickerClick = { backStack.removeLastOrNull() },
                    onPreviewClick = { backStack.add(ProjectPreview(route.projectId)) },
                    onBuildClick = { backStack.add(ProjectBuild(route.projectId)) },
                    onVersionsClick = { backStack.add(ProjectVersions(route.projectId)) },
                    onProjectSettingsClick = { backStack.add(ProjectSettings(route.projectId)) },
                    onAppSettingsClick = { backStack.add(ApplicationSettings) }
                )
            }
            entry<ProjectPicker> {
                val viewModel = hiltViewModel<ProjectsViewModel>()
                val projects by viewModel.projects.collectAsState()
                ProjectsScreen(
                    projects = projects,
                    modifier = Modifier.fillMaxSize(),
                    onSettingsClick = { backStack.add(ApplicationSettings) },
                    onProjectClick = { projectId -> backStack.add(ProjectChat(projectId)) },
                    onCreateProject = viewModel::createProject,
                    onUpdateProject = viewModel::updateProject,
                    onDeleteProject = viewModel::deleteProject
                )
            }
            entry<ProjectPreview> { route ->
                ProjectPreviewScreen(
                    projectId = route.projectId,
                    modifier = Modifier.fillMaxSize(),
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
            entry<ProjectBuild> { route ->
                ProjectBuildScreen(
                    projectId = route.projectId,
                    modifier = Modifier.fillMaxSize(),
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
            entry<ProjectVersions> { route ->
                ProjectVersionsScreen(
                    projectId = route.projectId,
                    modifier = Modifier.fillMaxSize(),
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
            entry<ProjectSettings> { route ->
                ProjectSettingsScreen(
                    projectId = route.projectId,
                    modifier = Modifier.fillMaxSize(),
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
            entry<ApplicationSettings> {
                SettingsScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBackClick = { backStack.removeLastOrNull() },
                    onAiProvidersClick = { backStack.add(ApplicationAiProviders) },
                    onLanguageClick = { backStack.add(ApplicationLanguageSettings) },
                    onTestApkEditorClick = { backStack.add(TestApkEditor) }
                )
            }
            entry<ApplicationAiProviders> {
                AiProvidersScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
            entry<ApplicationLanguageSettings> {
                LanguageSettingsScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
            entry<TestApkEditor> {
                TestApkEditorScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
