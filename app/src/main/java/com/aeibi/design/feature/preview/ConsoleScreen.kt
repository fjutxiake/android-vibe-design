package com.aeibi.design.feature.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeibi.design.R
import com.aeibi.design.data.runtimelogs.RuntimeLogEntry
import com.aeibi.design.theme.spacing
import kotlinx.coroutines.flow.Flow

/**
 * 运行日志面板——与 agent 的 read_runtime_logs 同一份数据（RuntimeLogStore）。
 * 点选日志（可多选）→ 顶栏「添加到聊天」批量作为输入框上方附件
 * （未来复用文件/截图引用）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConsoleScreen(
    logs: Flow<List<RuntimeLogEntry>>,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onClearClick: () -> Unit = {},
    onAddToChatClick: (List<RuntimeLogEntry>) -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    val messages by logs.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }
    // 选中集合随列表刷新收敛（日志被清/滚动淘汰后选中同步失效）
    val validKeys = remember(messages) { messages.map { it.key() }.toSet() }
    val selected = remember(messages, selectedKeys) {
        messages.filter { it.key() in selectedKeys }
    }
    val hasSelection = selected.isNotEmpty()

    fun clearSelection() {
        selectedKeys = emptySet()
    }

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
                    if (hasSelection) {
                        TextButton(onClick = {
                            onAddToChatClick(selected)
                            clearSelection()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.AddCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.preview_console_add_selected, selected.size),
                                modifier = Modifier.padding(start = spacing.xs)
                            )
                        }
                        IconButton(onClick = ::clearSelection) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.preview_console_cd_clear_selection)
                            )
                        }
                    } else {
                        IconButton(onClick = onClearClick, enabled = messages.isNotEmpty()) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.preview_console_cd_clear)
                            )
                        }
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
                verticalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                items(messages, key = { it.key() }) { entry ->
                    val isSelected = entry.key() in selectedKeys
                    ConsoleLogRow(
                        entry = entry,
                        selected = isSelected,
                        onToggle = {
                            selectedKeys = if (isSelected) {
                                selectedKeys - entry.key()
                            } else {
                                selectedKeys + entry.key()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsoleLogRow(
    entry: RuntimeLogEntry,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val level = entry.level
    val levelColor = when (level) {
        "ERROR" -> MaterialTheme.colorScheme.error
        "WARNING" -> MaterialTheme.colorScheme.tertiary
        "DEBUG" -> MaterialTheme.colorScheme.primary
        "TIP" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = level.take(1),
                color = levelColor,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.Top)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (level == "ERROR" || level == "WARNING") {
                        levelColor
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (entry.source.isNotEmpty()) {
                    Text(
                        text = entry.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun RuntimeLogEntry.key(): String = "$timestamp-${message.hashCode()}"
