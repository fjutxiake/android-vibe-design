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

    // 新条目到达(用户发送/AI 占位/载入历史/切换会话):一步定位到最新一条的尾部。
    // scrollToItem(index) 只把"条目顶部"对齐视口顶——长回复会被钉在开头;
    // 传一个远超条目高度的偏移,滚动会被钳制在列表末端,尾部贴住视口底。
    LaunchedEffect(sessionId, messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex, LIST_END_ITEM_OFFSET)
        }
    }
    // 生成中的文本增长:贴近底部时用 scrollBy 追平差量即可。
    // 这里绝不能 scrollToItem——它每个 chunk 都先把条目顶部钉回视口顶,
    // 再补偿滚回底部,一轮"顶→底"往返每秒几十次,就是抖动与
    // "跳回消息开头"的直接来源。scrollBy 只补增长的那一小段。
    LaunchedEffect(sessionId, lastStreamingLength) {
        if (lastStreamingLength > 0 && nearBottom) {
            withFrameNanos { } // 等一帧:增长后的高度完成测量再读 layoutInfo
            if (listState.isScrollInProgress) return@LaunchedEffect // 用户正在滚动:让位
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@LaunchedEffect
            if (last.index != info.totalItemsCount - 1) return@LaunchedEffect
            val gap = last.offset + last.size - info.viewportEndOffset
            if (gap > 0) {
                listState.scrollBy(gap.toFloat())
            }
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

/** 跟随判定的容差(像素):上翻超过该距离才脱离流式跟随。 */
private const val FOLLOW_SLOP_PX = 200

/** 远超单条消息可能高度的偏移量:让 scrollToItem 的钳制落到列表末端(尾部对齐视口底)。 */
private const val LIST_END_ITEM_OFFSET = 1_000_000
