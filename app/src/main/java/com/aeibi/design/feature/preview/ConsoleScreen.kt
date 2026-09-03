package com.aeibi.design.feature.preview

import android.webkit.ConsoleMessage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aeibi.design.R
import com.aeibi.design.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConsoleScreen(
    modifier: Modifier = Modifier,
    messages: List<ConsoleMessage>,
    onBackClick: () -> Unit = {},
    onClearClick: () -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preview_console_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onClearClick, enabled = messages.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.preview_console_cd_clear)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.preview_console_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                items(messages) { message ->
                    ConsoleMessageRow(message)
                }
            }
        }
    }
}

@Composable
private fun ConsoleMessageRow(message: ConsoleMessage, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    val color = when (message.messageLevel()) {
        ConsoleMessage.MessageLevel.ERROR -> MaterialTheme.colorScheme.error
        ConsoleMessage.MessageLevel.WARNING -> MaterialTheme.colorScheme.tertiary
        ConsoleMessage.MessageLevel.DEBUG -> MaterialTheme.colorScheme.primary
        ConsoleMessage.MessageLevel.TIP -> MaterialTheme.colorScheme.secondary
        ConsoleMessage.MessageLevel.LOG -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = message.messageLevel().name.take(1),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.Top)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message.message(),
                style = MaterialTheme.typography.bodyMedium,
                color = when (message.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR,
                    ConsoleMessage.MessageLevel.WARNING -> color
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            if (message.sourceId().isNotEmpty()) {
                Text(
                    text = "${message.sourceId()}:${message.lineNumber()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
