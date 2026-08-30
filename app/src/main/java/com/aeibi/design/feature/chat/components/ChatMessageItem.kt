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

/** 错误码 → 本地化文案;未归类的值按诊断原文展示。 */
@Composable
private fun localizedErrorText(error: String): String = when (error) {
    ChatViewModel.ERROR_NO_PROVIDER -> stringResource(R.string.chat_error_no_provider)
    ChatViewModel.ERROR_NETWORK -> stringResource(R.string.chat_error_network)
    ChatViewModel.ERROR_AUTH -> stringResource(R.string.chat_error_auth)
    ChatViewModel.ERROR_HTTP -> stringResource(R.string.chat_error_http)
    ChatViewModel.ERROR_PROTOCOL -> stringResource(R.string.chat_error_protocol)
    else -> error
}

/**
 * 单条消息气泡。生成中的 ASSISTANT 条目以 [streamingText] 展示内存中的
 * 实时聚合文本(库里此刻仍是空内容),流结束后由落库的完整内容接管。
 */
@Composable
fun ChatMessageItem(message: MessageEntry, modifier: Modifier = Modifier, streamingText: String? = null) {
    val spacing = MaterialTheme.spacing
    val dimensions = MaterialTheme.dimensions
    val isUser = message.role == MessageRole.USER
    val displayContent = if (message.status == MessageStatus.STREAMING && streamingText != null) {
        streamingText
    } else {
        message.content
    }

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
                    text = displayContent,
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
                            // 失败原因:已知错误码映射本地化文案,未知值为诊断原文。
                            message.error?.let { error ->
                                Text(
                                    text = localizedErrorText(error),
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
