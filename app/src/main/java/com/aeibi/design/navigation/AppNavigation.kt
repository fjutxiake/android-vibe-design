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
import com.aeibi.design.feature.build.ProjectBuildScreen
import com.aeibi.design.feature.projects.ProjectSetupScreen
import com.aeibi.design.feature.projects.ProjectsScreen
import com.aeibi.design.feature.projects.ProjectsViewModel
import com.aeibi.design.feature.projectsettings.ProjectSettingsScreen
import com.aeibi.design.feature.settings.SettingsScreen
import com.aeibi.design.feature.settings.ai.AiProvidersScreen
import com.aeibi.design.feature.settings.language.LanguageSettingsScreen
import com.aeibi.design.feature.templates.TemplateGalleryScreen
import com.aeibi.design.feature.versions.ProjectVersionsScreen
import com.aeibi.design.feature.workspace.ProjectWorkspaceScreen

@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(ProjectPicker)
    val projectsViewModel = hiltViewModel<ProjectsViewModel>()
    val projects by projectsViewModel.projects.collectAsState()

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
                    onBuildClick = { backStack.add(ProjectBuild(route.projectId)) },
                    onVersionsClick = { backStack.add(ProjectVersions(route.projectId)) },
                    onProjectSettingsClick = { backStack.add(ProjectSettings(route.projectId)) },
                    onAppSettingsClick = { backStack.add(ApplicationSettings) }
                )
            }
            entry<ProjectPicker> {
                ProjectsScreen(
                    projects = projects,
                    modifier = Modifier.fillMaxSize(),
                    onSettingsClick = { backStack.add(ApplicationSettings) },
                    onProjectClick = { projectId ->
                        projects.firstOrNull { it.id == projectId }?.let { project ->
                            backStack.add(
                                if (project.isInitialized) {
                                    ProjectChat(project.id)
                                } else {
                                    ProjectSetup(project.id)
                                }
                            )
                        }
                    },
                    onCreateProject = { name, description, iconUri, onResult ->
                        projectsViewModel.createProject(name, description, iconUri) { result ->
                            onResult(result)
                            result.onSuccess { project -> backStack.add(ProjectSetup(project.id)) }
                        }
                    },
                    onUpdateProject = projectsViewModel::updateProject,
                    onDeleteProject = projectsViewModel::deleteProject
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
            entry<ProjectSetup> { route ->
                ProjectSetupScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBackClick = { backStack.removeLastOrNull() },
                    onBrowseTemplatesClick = { backStack.add(TemplateGallery(route.projectId)) },
                    onStartBlankClick = { onResult ->
                        projectsViewModel.markInitialized(route.projectId) { result ->
                            onResult(result)
                            result.onSuccess {
                                if (backStack.lastOrNull() == route) {
                                    backStack.removeLastOrNull()
                                    backStack.add(ProjectChat(route.projectId))
                                }
                            }
                        }
                    },
                    onImportTemplateClick = { uri, onResult ->
                        projectsViewModel.initializeFromZip(route.projectId, uri) { result ->
                            onResult(result)
                            result.onSuccess {
                                if (backStack.lastOrNull() == route) {
                                    backStack.removeLastOrNull()
                                    backStack.add(ProjectChat(route.projectId))
                                }
                            }
                        }
                    }
                )
            }
            entry<TemplateGallery> { route ->
                TemplateGalleryScreen(
                    projectId = route.projectId,
                    onBackClick = { backStack.removeLastOrNull() },
                    onProjectInitialized = { projectId ->
                        backStack.removeLastOrNull()
                        backStack.removeLastOrNull()
                        backStack.add(ProjectChat(projectId))
                    }
                )
            }
            entry<ApplicationSettings> {
                SettingsScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBackClick = { backStack.removeLastOrNull() },
                    onAiProvidersClick = { backStack.add(ApplicationAiProviders) },
                    onLanguageClick = { backStack.add(ApplicationLanguageSettings) }
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
        }
    )
}
