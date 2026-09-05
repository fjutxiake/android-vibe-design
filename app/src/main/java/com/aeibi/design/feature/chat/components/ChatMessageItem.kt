package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aeibi.design.R
import com.aeibi.design.feature.chat.ChatMessageStatus
import com.aeibi.design.feature.chat.ChatRole
import com.aeibi.design.feature.chat.ChatTimelineItem
import com.aeibi.design.theme.spacing

@Composable
fun ChatMessageItem(message: ChatTimelineItem.Message, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    val isUser = message.role == ChatRole.USER
    // markdown 只渲染 assistant 的完成态——流式/中间态保持纯文本（避免 ** 闪烁与重复解析）。
    val renderMarkdown = !isUser && message.status == ChatMessageStatus.COMPLETE
    val displayedText = when (message.status) {
        ChatMessageStatus.WORKING -> message.text.ifBlank { stringResource(R.string.chat_agent_working) }
        ChatMessageStatus.CANCELLED -> listOf(
            message.text,
            stringResource(R.string.chat_agent_cancelled)
        ).filter(String::isNotBlank).joinToString("\n\n")
        ChatMessageStatus.INCOMPLETE -> listOf(
            message.text,
            stringResource(R.string.chat_agent_incomplete)
        ).filter(String::isNotBlank).joinToString("\n\n")
        ChatMessageStatus.FAILED -> message.text.ifBlank { stringResource(R.string.chat_agent_failed) }
        ChatMessageStatus.COMPLETE -> message.text
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            shape = MaterialTheme.shapes.large
        ) {
            if (renderMarkdown) {
                val bubbleColor = MaterialTheme.colorScheme.surfaceContainer
                MarkdownText(
                    text = displayedText,
                    modifier = Modifier.padding(spacing.sm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.contentColorFor(bubbleColor)
                )
            } else {
                Text(
                    displayedText,
                    modifier = Modifier.padding(spacing.sm),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
