package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aeibi.design.R
import com.aeibi.design.data.messages.MessageEntry
import com.aeibi.design.data.messages.MessageRole
import com.aeibi.design.data.messages.MessageStatus
import com.aeibi.design.theme.dimensions
import com.aeibi.design.theme.spacing

@Composable
fun ChatMessageItem(message: MessageEntry, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    val dimensions = MaterialTheme.dimensions
    val isUser = message.role == MessageRole.USER

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = dimensions.chatBubbleMaxWidth)
        ) {
            Column(modifier = Modifier.padding(spacing.sm)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium
                )
                // 中断或失败的回复不能伪装成已成功完成的消息。
                if (message.role == MessageRole.ASSISTANT && message.status != MessageStatus.COMPLETED) {
                    val failed = message.status == MessageStatus.FAILED
                    Text(
                        text = if (failed) {
                            stringResource(R.string.chat_status_failed)
                        } else {
                            stringResource(R.string.chat_status_interrupted)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}
