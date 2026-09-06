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
    val streamingTimeline = uiState.streamingResponses.flatMap { response ->
        listOfNotNull(
            response.thinkingText.takeIf(String::isNotBlank)?.let {
                ChatTimelineItem.Thinking(
                    id = "streaming-thinking-${response.id}",
                    text = it,
                    isStreaming = true
                )
            },
            response.text.takeIf(String::isNotBlank)?.let {
                ChatTimelineItem.Message(
                    id = "streaming-assistant-${response.id}",
                    role = ChatRole.ASSISTANT,
                    text = it,
                    status = ChatMessageStatus.WORKING
                )
            }
        )
    }
    val timeline = uiState.timeline + streamingTimeline + listOfNotNull(
        uiState.streamingText?.let {
            ChatTimelineItem.Message(
                id = "streaming-assistant",
                role = ChatRole.ASSISTANT,
                text = it,
                status = uiState.streamingStatus
            )
        }
    )

    LaunchedEffect(projectId, sessionId) {
        viewModel.bind(projectId, sessionId)
    }
    Column(modifier = modifier.fillMaxSize()) {
        ChatMessageList(
            projectId = projectId,
            sessionId = sessionId,
            timeline = timeline,
            isLoading = uiState.sessionId != sessionId || uiState.isLoadingSession,
            isRunning = uiState.isRunning,
            modifier = Modifier.weight(1f)
        )
        ChatComposer(
            input = uiState.input,
            attachment = uiState.attachment,
            isRunning = uiState.isRunning,
            onInputChange = viewModel::updateInput,
            onRemoveAttachment = viewModel::removeAttachment,
            onSend = { viewModel.send(onSessionCreated) },
            onCancel = viewModel::cancel
        )
    }
}
