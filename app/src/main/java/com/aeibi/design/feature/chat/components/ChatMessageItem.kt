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
import com.aeibi.design.feature.chat.ChatViewModel
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
                // 非完成状态的回复要有明确的可视状态,不能伪装成已完成的空消息。
                if (message.role == MessageRole.ASSISTANT) {
                    when (message.status) {
                        MessageStatus.COMPLETED -> Unit
                        MessageStatus.STREAMING -> Text(
                            text = stringResource(R.string.chat_status_streaming),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MessageStatus.INTERRUPTED -> Text(
                            text = stringResource(R.string.chat_status_interrupted),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MessageStatus.FAILED -> {
                            Text(
                                text = stringResource(R.string.chat_status_failed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            // 失败原因:no_provider 错误码映射本地化文案,其余为诊断原文。
                            message.error?.let { error ->
                                Text(
                                    text = if (error == ChatViewModel.ERROR_NO_PROVIDER) {
                                        stringResource(R.string.chat_error_no_provider)
                                    } else {
                                        error
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
