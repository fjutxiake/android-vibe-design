package com.aeibi.design.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeibi.design.feature.chat.components.ChatComposer
import com.aeibi.design.feature.chat.components.ChatMessageList

@Composable
fun ChatScreen(
    projectId: String,
    sessionId: String?,
    modifier: Modifier = Modifier,
    onSessionCreated: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(projectId, sessionId) {
        viewModel.bind(projectId, sessionId)
    }
    LaunchedEffect(uiState.sessionId, sessionId) {
        if (sessionId == null) uiState.sessionId?.let(onSessionCreated)
    }

    Column(modifier = modifier.fillMaxSize()) {
        ChatMessageList(
            projectId = projectId,
            sessionId = sessionId,
            timeline = uiState.timeline,
            modifier = Modifier.weight(1f)
        )
        ChatComposer(
            input = uiState.input,
            isRunning = uiState.isRunning,
            onInputChange = viewModel::updateInput,
            onSend = viewModel::send,
            onCancel = viewModel::cancel
        )
    }
}
