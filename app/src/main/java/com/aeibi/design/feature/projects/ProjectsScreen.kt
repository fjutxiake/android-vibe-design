package com.aeibi.design.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.aeibi.design.data.projects.Project
import com.aeibi.design.theme.VibeDesignTheme
import com.aeibi.design.theme.spacing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProjectsScreen(
    projects: List<Project>,
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {},
    onProjectClick: (String) -> Unit = {},
    onCreateProject: (
        name: String,
        description: String,
        iconUri: String?,
        onResult: (Result<Unit>) -> Unit
    ) -> Unit = { _, _, _, _ -> },
    onUpdateProject: (
        id: String,
        name: String,
        description: String,
        iconUri: String?,
        onResult: (Result<Unit>) -> Unit
    ) -> Unit = { _, _, _, _, _ -> },
    onDeleteProject: (id: String, onResult: (Result<Unit>) -> Unit) -> Unit = { _, _ -> }
) {
    val spacing = MaterialTheme.spacing
    var showNewProjectSheet by rememberSaveable { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var createError by rememberSaveable { mutableStateOf<String?>(null) }
    var editError by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Vibe Design") },
                actions = {
                    IconButton(
                        onClick = { showNewProjectSheet = true },
                        modifier = Modifier.testTag("new_project_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "新建项目")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(imageVector = Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            items(projects, key = { it.id }) { project ->
                ProjectListItem(
                    project = project,
                    onClick = { onProjectClick(project.id) },
                    onLongClick = {
                        editError = null
                        editingProject = project
                    }
                )
            }
            if (projects.isEmpty()) {
                item { EmptyProjectsState(modifier = Modifier.fillParentMaxSize()) }
            }
        }
    }

    if (showNewProjectSheet) {
        NewProjectBottomSheet(
            onDismiss = {
                showNewProjectSheet = false
                createError = null
            },
            onCreate = { name, description, iconUri ->
                creating = true
                createError = null
                // 创建成功才收起面板;失败就留在原地并给出提示,已填的内容不丢。
                onCreateProject(name, description, iconUri) { result ->
                    creating = false
                    result
                        .onSuccess { showNewProjectSheet = false }
                        .onFailure { createError = "创建项目失败,请重试" }
                }
            },
            errorMessage = createError,
            submitting = creating
        )
    }

    editingProject?.let { project ->
        EditProjectBottomSheet(
            project = project,
            onDismiss = {
                editingProject = null
                editError = null
            },
            onSave = { name, description, iconUri ->
                editing = true
                editError = null
                onUpdateProject(project.id, name, description, iconUri) { result ->
                    editing = false
                    result
                        .onSuccess { editingProject = null }
                        .onFailure { editError = "保存项目失败,请重试" }
                }
            },
            onDelete = {
                editing = true
                editError = null
                onDeleteProject(project.id) { result ->
                    editing = false
                    result
                        .onSuccess { editingProject = null }
                        .onFailure { editError = "删除项目失败,请重试" }
                }
            },
            errorMessage = editError,
            submitting = editing
        )
    }
}

@Composable
private fun EmptyProjectsState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.testTag("empty_projects"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "还没有项目,点击右上角 + 创建第一个",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProjectsScreenPreview() {
    VibeDesignTheme(dynamicColor = false) { ProjectsScreen(projects = emptyList()) }
}
