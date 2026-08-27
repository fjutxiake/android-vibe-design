package com.aeibi.design.feature.workspace

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aeibi.design.R
import com.aeibi.design.feature.chat.ChatScreen
import com.aeibi.design.feature.projects.ProjectsViewModel
import com.aeibi.design.feature.sessions.SessionDrawer
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProjectWorkspaceScreen(
    projectId: String,
    modifier: Modifier = Modifier,
    onProjectPickerClick: () -> Unit = {},
    onPreviewClick: () -> Unit = {},
    onBuildClick: () -> Unit = {},
    onVersionsClick: () -> Unit = {},
    onProjectSettingsClick: () -> Unit = {},
    onAppSettingsClick: () -> Unit = {},
    viewModel: ProjectsViewModel = hiltViewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedSessionId by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var showProjectActions by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val project by viewModel.observeProject(projectId).collectAsState(initial = null)
    // lambda 中无法调用 stringResource,先在组合作用域取好文本再闭包引用。
    val deleteFailedText = stringResource(R.string.projects_delete_failed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawer(
                projectId = projectId,
                selectedSessionId = selectedSessionId,
                onSessionSelected = { sessionId ->
                    selectedSessionId = sessionId
                    scope.launch { drawerState.close() }
                },
                onCurrentSessionDeleted = {
                    selectedSessionId = null
                    scope.launch { drawerState.close() }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
                ProjectTopBar(
                    projectName = project?.name ?: stringResource(R.string.workspace_unnamed_project),
                    onBackClick = onProjectPickerClick,
                    onSessionsClick = { scope.launch { drawerState.open() } },
                    onPreviewClick = onPreviewClick,
                    onMoreClick = { showProjectActions = true }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            ChatScreen(
                projectId = projectId,
                sessionId = selectedSessionId,
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
            title = { Text(stringResource(R.string.delete_project_title)) },
            text = { Text(stringResource(R.string.delete_project_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        // 删除真正成功才退回项目列表;失败就留在当前项目并提示,不假装已经删掉。
                        viewModel.deleteProject(projectId) { result ->
                            result
                                .onSuccess { onProjectPickerClick() }
                                .onFailure {
                                    scope.launch { snackbarHostState.showSnackbar(deleteFailedText) }
                                }
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
