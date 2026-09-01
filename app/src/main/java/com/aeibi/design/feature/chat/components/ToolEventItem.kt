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
import com.aeibi.design.ai.AgentEvent
import com.aeibi.design.theme.spacing

@Composable
fun ToolEventItem(event: AgentEvent, modifier: Modifier = Modifier) {
    if (event is AgentEvent.TextDelta) return
    val spacing = MaterialTheme.spacing
    val started = event is AgentEvent.ToolStarted
    val name = when (event) {
        is AgentEvent.ToolStarted -> event.name
        is AgentEvent.ToolFinished -> event.name
    }
    Row(modifier = modifier.padding(horizontal = spacing.sm)) {
        Icon(
            imageVector = if (started) Icons.Default.Sync else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                if (started) R.string.chat_tool_started else R.string.chat_tool_finished,
                name
            ),
            modifier = Modifier.padding(start = spacing.xs),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
