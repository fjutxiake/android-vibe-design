package com.aeibi.design.feature.preview

import android.webkit.ConsoleMessage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.aeibi.design.R
import com.aeibi.design.theme.spacing

/**
 * Console 子界面——预览页的调试台（对应 DevTools Console）：
 * 全级别消息列表（ERROR/WARNING/INFO/DEBUG），行级样式 + 源/行号。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConsoleScreen(
    messages: List<ConsoleMessage>,
    onClearClick: () -> Unit = {},
    onCloseClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.preview_console_title)) },
            navigationIcon = {
                IconButton(onClick = onCloseClick) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                }
            },
            actions = {
                IconButton(onClick = onClearClick) {
                    Icon(Icons.Filled.ClearAll, contentDescription = stringResource(R.string.preview_console_clear))
                }
            }
        )
        if (messages.isEmpty()) {
            Text(
                text = stringResource(R.string.preview_console_empty),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.lg),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(messages, key = { System.identityHashCode(it) }) { message ->
                    ConsoleMessageRow(message)
                }
            }
        }
    }
}

@Composable
private fun ConsoleMessageRow(message: ConsoleMessage) {
    val spacing = MaterialTheme.spacing
    val color = when (message.messageLevel()) {
        ConsoleMessage.MessageLevel.ERROR -> MaterialTheme.colorScheme.error
        ConsoleMessage.MessageLevel.WARNING -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Text(
            text = message.messageLevel().name.take(1),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.Top)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message.message(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (message.sourceId().isNotEmpty()) {
                Text(
                    text = "${message.sourceId()}:${message.lineNumber()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
