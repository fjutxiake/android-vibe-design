package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aeibi.design.R
import com.aeibi.design.feature.chat.ChatTimelineItem
import com.aeibi.design.theme.spacing

@Composable
fun ToolEventItem(item: ChatTimelineItem.ToolCall, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    Row(modifier = modifier.padding(horizontal = spacing.sm)) {
        Icon(
            imageVector = if (item.isFinished) Icons.Default.CheckCircle else Icons.Default.Sync,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                if (item.isFinished) R.string.chat_tool_finished else R.string.chat_tool_started,
                item.name
            ),
            modifier = Modifier.padding(start = spacing.xs),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
