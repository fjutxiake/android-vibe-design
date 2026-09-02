package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aeibi.design.R
import com.aeibi.design.feature.chat.ChatTimelineItem
import com.aeibi.design.theme.spacing

@Composable
fun ToolEventItem(item: ChatTimelineItem, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    val isFinished = item is ChatTimelineItem.ToolResult
    val isError = (item as? ChatTimelineItem.ToolResult)?.isError == true
    val name = when (item) {
        is ChatTimelineItem.ToolCall -> item.name
        is ChatTimelineItem.ToolResult -> item.name
        else -> return
    }
    Row(
        modifier = modifier.padding(horizontal = spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                isError -> Icons.Default.ErrorOutline
                isFinished -> Icons.Default.CheckCircle
                else -> Icons.Default.Sync
            },
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                if (isFinished) R.string.chat_tool_finished else R.string.chat_tool_started,
                name
            ),
            modifier = Modifier.padding(start = spacing.xs),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
