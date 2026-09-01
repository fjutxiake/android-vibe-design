package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aeibi.design.R
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

    LaunchedEffect(isRunning) {
        if (isRunning) followTail = true
    }

    LaunchedEffect(listState) {
        var userWasScrolling = false
        snapshotFlow { listState.isScrollInProgress }.collect { isScrolling ->
            if (isScrolling) {
                userWasScrolling = true
            } else if (userWasScrolling) {
                followTail = listState.isAtBottom()
                userWasScrolling = false
            }
        }
    }

    LaunchedEffect(timeline.lastOrNull(), timeline.size, followTail, isLoading) {
        if (!isLoading && followTail && timeline.isNotEmpty()) {
            listState.scrollToItem(timeline.size)
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

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = spacing.sm),
            contentPadding = PaddingValues(vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            items(timeline, key = ChatTimelineItem::id) { item ->
                when (item) {
                    is ChatTimelineItem.Message -> ChatMessageItem(item)
                    is ChatTimelineItem.ToolCall -> ToolEventItem(item)
                }
            }
            item(key = "chat-bottom-anchor") { Spacer(Modifier.size(1.dp)) }
        }

        if (!followTail) {
            FloatingActionButton(
                onClick = { followTail = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(spacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.chat_cd_scroll_to_bottom)
                )
            }
        }
    }
}

private fun LazyListState.isAtBottom(): Boolean {
    val layoutInfo = layoutInfo
    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return true
    return lastVisibleItem.index == layoutInfo.totalItemsCount - 1 &&
        lastVisibleItem.offset + lastVisibleItem.size <= layoutInfo.viewportEndOffset
}
