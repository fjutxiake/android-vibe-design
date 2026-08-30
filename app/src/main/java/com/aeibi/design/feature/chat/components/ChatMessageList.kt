package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aeibi.design.data.messages.MessageEntry
import com.aeibi.design.theme.spacing

@Composable
fun ChatMessageList(
    projectId: String,
    sessionId: String?,
    messages: List<MessageEntry>,
    modifier: Modifier = Modifier,
    streamingTexts: Map<String, String> = emptyMap()
) {
    if (messages.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ChatEmptyState(projectId = projectId, sessionId = sessionId)
        }
        return
    }

    val spacing = MaterialTheme.spacing
    val listState = rememberLazyListState()
    // 生成中的文本在增长:跟随最新一条,而不只在新条目到达时滚一次。
    val lastStreamingLength = messages.lastOrNull()?.let { streamingTexts[it.id]?.length } ?: 0
    LaunchedEffect(messages.size, lastStreamingLength) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        items(messages, key = { it.id }) { message ->
            ChatMessageItem(message = message, streamingText = streamingTexts[message.id])
        }
    }
}
