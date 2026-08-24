package com.aeibi.design.feature.workspace

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aeibi.design.feature.chat.ChatScreen
import com.aeibi.design.feature.projects.ProjectsViewModel
import com.aeibi.design.feature.sessions.SessionDrawer
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProjectWorkspaceScreen(
    projectId: String,
    sessionId: String?,
    modifier: Modifier = Modifier,
    onProjectPickerClick: () -> Unit = {},
    onNewChatClick: () -> Unit = {},
    onSessionSelected: (String) -> Unit = {},
    onPreviewClick: () -> Unit = {},
    onBuildClick: () -> Unit = {},
    onVersionsClick: () -> Unit = {},
    onProjectSettingsClick: () -> Unit = {},
    onAppSettingsClick: () -> Unit = {},
    viewModel: ProjectsViewModel = hiltViewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showProjectActions by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val project by viewModel.observeProject(projectId).collectAsState(initial = null)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawer(
                selectedSessionId = sessionId,
                onNewChatClick = {
                    scope.launch { drawerState.close() }
                    onNewChatClick()
                },
                onSessionSelected = { selectedSessionId ->
                    scope.launch { drawerState.close() }
                    onSessionSelected(selectedSessionId)
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
                ProjectTopBar(
                    projectName = project?.name ?: "未命名项目",
                    onBackClick = onProjectPickerClick,
                    onSessionsClick = { scope.launch { drawerState.open() } },
                    onPreviewClick = onPreviewClick,
                    onMoreClick = { showProjectActions = true }
                )
            }
        ) { innerPadding ->
            ChatScreen(
                projectId = projectId,
                sessionId = sessionId,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
        }
    }

    if (showProjectActions) {
        ProjectActionsSheet(
            onDismiss = { showProjectActions = false },
            onBuildClick = onBuildClick,
            onVersionsClick = onVersionsClick,
            onProjectSettingsClick = onProjectSettingsClick,
            onAppSettingsClick = onAppSettingsClick,
            onDeleteClick = { showDeleteConfirm = true }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除项目") },
            text = { Text("将删除该项目及其全部会话,此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteProject(projectId) { onProjectPickerClick() }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}
