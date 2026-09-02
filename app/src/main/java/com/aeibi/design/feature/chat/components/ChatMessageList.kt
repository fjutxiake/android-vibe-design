package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
    var followTail by rememberSaveable(sessionId) { mutableStateOf(true) }
    var isAutoScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning) {
        if (isRunning) followTail = true
    }

    LaunchedEffect(listState) {
        var userWasScrolling = false
        snapshotFlow { listState.isScrollInProgress to isAutoScrolling }.collect { (isScrolling, autoScrolling) ->
            if (isScrolling && !autoScrolling) {
                userWasScrolling = true
                followTail = false
            } else if (userWasScrolling) {
                followTail = listState.isAtBottom()
                userWasScrolling = false
            }
        }
    }

    LaunchedEffect(sessionId, timeline.size, followTail, isLoading) {
        if (!isLoading && followTail && timeline.isNotEmpty()) {
            isAutoScrolling = true
            try {
                listState.scrollToItem(timeline.lastIndex, Int.MAX_VALUE)
            } finally {
                isAutoScrolling = false
            }
        }
    }

    LaunchedEffect(sessionId, timeline.lastOrNull(), followTail, isLoading) {
        if (!isLoading && followTail && timeline.isNotEmpty()) {
            withFrameNanos { }
            if (listState.isScrollInProgress) return@LaunchedEffect

            val layoutInfo = listState.layoutInfo
            val lastItem = layoutInfo.visibleItemsInfo.lastOrNull { it.index == timeline.lastIndex }
                ?: return@LaunchedEffect
            val overflow = lastItem.offset + lastItem.size -
                (layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding)
            if (overflow > 0) {
                isAutoScrolling = true
                try {
                    listState.scrollBy(overflow.toFloat())
                } finally {
                    isAutoScrolling = false
                }
            }
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
        modifier = modifier.fillMaxSize().padding(horizontal = spacing.sm),
        contentPadding = PaddingValues(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        itemsIndexed(timeline, key = { _, item -> item.id }) { _, item ->
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

private fun LazyListState.isAtBottom() = !canScrollForward
