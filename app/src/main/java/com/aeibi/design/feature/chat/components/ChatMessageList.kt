package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aeibi.design.feature.chat.ChatTimelineItem
import com.aeibi.design.theme.spacing

@Composable
fun ChatMessageList(
    projectId: String,
    sessionId: String?,
    timeline: List<ChatTimelineItem>,
    isLoading: Boolean,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()

    var followTail by rememberSaveable(sessionId) { mutableStateOf(true) }

    LaunchedEffect(listState, isDragged) {
        snapshotFlow {
            isDragged to listState.canScrollForward
        }.collect { (dragged, canScrollForward) ->
            if (dragged && canScrollForward) followTail = false
            if (!canScrollForward) followTail = true
        }
    }

    LaunchedEffect(isRunning) {
        if (isRunning) followTail = true
    }

    LaunchedEffect(sessionId, followTail, timeline.lastOrNull(), timeline.size, isLoading) {
        if (!isLoading && followTail && timeline.isNotEmpty() && !listState.isScrollInProgress) {
            listState.scrollToItem(timeline.lastIndex, Int.MAX_VALUE)
        }
    }

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (timeline.isEmpty()) {
        ChatEmptyState(projectId = projectId, sessionId = sessionId, modifier = modifier.fillMaxSize())
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.sm),
        contentPadding = PaddingValues(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        items(timeline, key = { it.id }) { item ->
            Box {
                when (item) {
                    is ChatTimelineItem.Message -> ChatMessageItem(item)
                    is ChatTimelineItem.Thinking -> ThinkingItem(item)
                    is ChatTimelineItem.ToolCall -> ToolEventItem(item)
                    is ChatTimelineItem.ToolResult -> ToolEventItem(item)
                }
            }
        }
    }
}
