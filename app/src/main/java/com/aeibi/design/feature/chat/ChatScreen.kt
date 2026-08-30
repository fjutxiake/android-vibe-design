package com.aeibi.design.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeibi.design.feature.chat.components.ChatComposer
import com.aeibi.design.feature.chat.components.ChatMessageList
import com.aeibi.design.feature.chat.components.SessionProviderBar
import com.aeibi.design.feature.chat.components.SessionProviderSheet

@Composable
fun ChatScreen(
    projectId: String,
    sessionId: String?,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel()
) {
    LaunchedEffect(sessionId) {
        viewModel.bind(sessionId)
    }
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streamingTexts by viewModel.streamingTexts.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val sessionProvider by viewModel.sessionProvider.collectAsStateWithLifecycle()
    var showProviderSheet by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        if (sessionId != null) {
            SessionProviderBar(
                state = sessionProvider,
                onClick = { showProviderSheet = true }
            )
        }
        ChatMessageList(
            projectId = projectId,
            sessionId = sessionId,
            messages = messages,
            modifier = Modifier.weight(1f),
            streamingTexts = streamingTexts
        )
        ChatComposer(
            enabled = sessionId != null,
            onSendMessage = viewModel::sendMessage,
            isGenerating = isGenerating,
            onStopGenerating = viewModel::stopGenerating
        )
    }

    if (showProviderSheet && sessionId != null) {
        SessionProviderSheet(
            state = sessionProvider,
            onDismiss = { showProviderSheet = false },
            onSelect = viewModel::selectSessionProvider
        )
    }
}
