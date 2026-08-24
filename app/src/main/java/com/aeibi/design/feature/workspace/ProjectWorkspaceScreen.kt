package com.aeibi.design.feature.workspace

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aeibi.design.feature.chat.ChatScreen
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
    onAppSettingsClick: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedSessionId by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var showProjectActions by rememberSaveable { mutableStateOf(false) }

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
                    projectName = "未命名项目",
                    onBackClick = onProjectPickerClick,
                    onSessionsClick = { scope.launch { drawerState.open() } },
                    onPreviewClick = onPreviewClick,
                    onMoreClick = { showProjectActions = true }
                )
            }
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
            onAppSettingsClick = onAppSettingsClick
        )
    }
}
