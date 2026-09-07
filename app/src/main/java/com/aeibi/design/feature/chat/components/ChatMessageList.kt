package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aeibi.design.feature.chat.ChatTimelineItem
import com.aeibi.design.theme.spacing

/**
 * 消息列表——Column（非虚拟化）。
 *
 * 真机二分实验结论：卡源 = markdown 渲染树组合/布局成本（纯文本同文本量不卡）。
 * Column 让消息**常驻组合**——组合成本只在消息出现时付一次，滚动零组合——
 * 用「消息出现时的一次性成本」换「滚动全程无卡」。
 */
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
    val scrollState = rememberScrollState()

    var followTail by rememberSaveable(sessionId) { mutableStateOf(true) }

    LaunchedEffect(scrollState) {
        snapshotFlow {
            scrollState.isScrollInProgress to scrollState.value
        }.collect { (dragging, _) ->
            if (dragging) followTail = false
            if (scrollState.value >= scrollState.maxValue) followTail = true
        }
    }

    LaunchedEffect(isRunning) {
        if (isRunning) followTail = true
    }

    LaunchedEffect(sessionId, followTail, timeline.lastOrNull(), timeline.size, isLoading) {
        if (!isLoading && followTail && timeline.isNotEmpty()) {
            // 收敛式补滚：消息内容渲染/增长撑高后持续滚到底，直到布局稳定
            repeat(MAX_FOLLOW_ROUNDS) {
                scrollState.scrollTo(scrollState.maxValue)
                withFrameNanos { }
                if (scrollState.value >= scrollState.maxValue) return@repeat
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        timeline.forEach { item ->
            when (item) {
                is ChatTimelineItem.Message -> ChatMessageItem(item, Modifier.fillMaxWidth())
                is ChatTimelineItem.Thinking -> ThinkingItem(item, Modifier.fillMaxWidth())
                is ChatTimelineItem.ToolCall -> ToolEventItem(item)
                is ChatTimelineItem.ToolResult -> ToolEventItem(item)
            }
        }
    }
}

private const val MAX_FOLLOW_ROUNDS = 12
