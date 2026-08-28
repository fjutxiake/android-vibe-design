package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aeibi.design.data.messages.MessageEntity
import com.aeibi.design.data.messages.MessageRole
import com.aeibi.design.theme.dimensions
import com.aeibi.design.theme.spacing

@Composable
fun ChatMessageItem(message: MessageEntity, modifier: Modifier = Modifier) {
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
            Text(
                text = message.content,
                modifier = Modifier.padding(spacing.sm),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
