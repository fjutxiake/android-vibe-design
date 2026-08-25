package com.aeibi.design.feature.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeibi.design.data.sessions.SessionEntity
import com.aeibi.design.theme.spacing
import kotlinx.coroutines.launch

@Composable
fun SessionDrawer(
    projectId: String,
    selectedSessionId: String?,
    onSessionSelected: (String) -> Unit,
    onCurrentSessionDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: SessionViewModel = hiltViewModel()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val spacing = MaterialTheme.spacing
    var renameTarget by remember { mutableStateOf<SessionEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<SessionEntity?>(null) }

    LaunchedEffect(projectId) {
        viewModel.observe(projectId)
    }

    ModalDrawerSheet(
        modifier = modifier
            .fillMaxWidth(0.86f)
            .fillMaxHeight()
    ) {
        Column(modifier = Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Button(
                onClick = {
                    scope.launch {
                        onSessionSelected(viewModel.createSession(projectId))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text("新建会话")
            }
            SessionList(
                state = listState,
                sessions = sessions,
                selectedSessionId = selectedSessionId,
                onSessionSelected = onSessionSelected,
                onSessionRename = { renameTarget = it },
                onSessionDelete = { deleteTarget = it },
                modifier = Modifier.weight(1f)
            )
        }
    }

    renameTarget?.let { session ->
        RenameSessionDialog(
            initialTitle = session.title,
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                renameTarget = null
                scope.launch { viewModel.renameSession(session.id, title) }
            }
        )
    }

    deleteTarget?.let { session ->
        DeleteSessionDialog(
            sessionTitle = session.title,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    viewModel.deleteSession(session.id)
                    if (session.id == selectedSessionId) {
                        onCurrentSessionDeleted()
                    }
                }
            }
        )
    }
}

@Composable
private fun RenameSessionDialog(initialTitle: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名会话") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("名称") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onConfirm(title.trim()) }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun DeleteSessionDialog(sessionTitle: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除会话") },
        text = { Text("确定删除「$sessionTitle」吗？删除后不可恢复。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
