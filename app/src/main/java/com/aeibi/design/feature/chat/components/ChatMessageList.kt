package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
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
    // 是否贴近列表底部:流式增长的跟随条件。用户主动上翻即脱离跟随,
    // 翻回底部附近自动恢复——避免抢占用户的手动滚动。
    val nearBottom by remember {
        derivedStateOf { listState.isNearBottom() }
    }
    val lastStreamingLength = messages.lastOrNull()?.let { streamingTexts[it.id]?.length } ?: 0

    // 新条目到达(用户发送/AI 占位/载入历史/切换会话):始终定位到最新消息。
    LaunchedEffect(sessionId, messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToListEnd()
        }
    }
    // 生成中的文本增长:仅在贴近底部时跟随,用即时滚动追平尾部。
    LaunchedEffect(sessionId, lastStreamingLength) {
        if (lastStreamingLength > 0 && nearBottom) {
            listState.scrollToListEnd()
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

/** 判定当前视口是否停在列表底部附近(容差内)。 */
private fun LazyListState.isNearBottom(): Boolean {
    val info = layoutInfo
    if (info.totalItemsCount == 0) return true
    val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return true
    if (lastVisible.index != info.totalItemsCount - 1) return false
    return lastVisible.offset + lastVisible.size <= info.viewportEndOffset + FOLLOW_SLOP_PX
}

/**
 * 滚动到列表末端(最后一条的尾部对齐视口底部)。
 *
 * scrollToItem(index) 对齐的是"条目顶部在视口顶":最后一条比屏幕长时
 * (长文流式输出),最新内容仍在视口外——表现为消息头部卡在顶部、
 * 增长部分看不见。这里在定位后再补滚条目底部超出视口的部分,把
 * 尾部带进视野;条目比视口短时 scrollToItem 本身已钳制在列表末端。
 */
private suspend fun LazyListState.scrollToListEnd() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    scrollToItem(lastIndex)
    withFrameNanos { } // 等一帧让快照滚动完成测量,layoutInfo 才是滚动后的
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return
    val overscroll = last.offset + last.size - info.viewportEndOffset
    if (overscroll > 0) {
        // 超出末端的部分由 scrollBy 自动钳制,不会滚过头。
        scrollBy((overscroll + 1).toFloat())
    }
}

/** 跟随判定的容差(像素):上翻超过该距离才脱离流式跟随。 */
private const val FOLLOW_SLOP_PX = 200
