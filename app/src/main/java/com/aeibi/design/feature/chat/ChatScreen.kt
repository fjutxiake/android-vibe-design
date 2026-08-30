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
    viewModel: ChatViewModel = hiltViewModel()
) {
    LaunchedEffect(sessionId) {
        viewModel.bind(sessionId)
    }
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streamingTexts by viewModel.streamingTexts.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        ChatMessageList(
            projectId = projectId,
            sessionId = sessionId,
            messages = messages,
            modifier = Modifier.weight(1f),
            streamingTexts = streamingTexts
        )
        ChatComposer(
            enabled = sessionId != null,
            onSendMessage = viewModel::sendMessage
        )
    }
}
