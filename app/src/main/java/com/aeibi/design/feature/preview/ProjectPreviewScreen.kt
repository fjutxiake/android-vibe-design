package com.aeibi.design.feature.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aeibi.design.R
import com.aeibi.design.feature.workspace.PreviewStatus
import com.aeibi.design.feature.workspace.PreviewUiState
import com.aeibi.design.theme.spacing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ProjectPreviewScreen(
    state: PreviewUiState,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    onBackClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onToggleBackendClick: () -> Unit = {},
    onFullscreenClick: () -> Unit = {},
    onConsoleClick: () -> Unit = {},
    onClearConsoleClick: () -> Unit = {},
    content: @Composable (Modifier) -> Unit = {}
) {
    val running = state.status == PreviewStatus.RUNNING
    val transitioning = state.status in listOf(PreviewStatus.STARTING, PreviewStatus.STOPPING)
    var showConsole by rememberSaveable { mutableStateOf(false) }
    val errorCount = state.errorMessages.size

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (!fullscreen) {
                TopAppBar(
                    title = { Text(stringResource(R.string.preview_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefreshClick, enabled = running) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.preview_cd_refresh)
                            )
                        }
                        IconButton(
                            onClick = {
                                showConsole = !showConsole
                                onConsoleClick()
                            },
                            enabled = running
                        ) {
                            Box {
                                Icon(
                                    Icons.Filled.Terminal,
                                    contentDescription = stringResource(R.string.preview_cd_console)
                                )
                                if (errorCount > 0) {
                                    Badge(
                                        modifier = Modifier.align(Alignment.TopEnd),
                                        containerColor = MaterialTheme.colorScheme.error
                                    ) {
                                        Text(
                                            text = errorCount.toString(),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(onClick = onToggleBackendClick, enabled = !transitioning) {
                            Icon(
                                if (running) Icons.Filled.StopCircle else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (running) R.string.preview_cd_stop else R.string.preview_cd_start
                                )
                            )
                        }
                        IconButton(onClick = onFullscreenClick, enabled = running) {
                            Icon(
                                Icons.Filled.Fullscreen,
                                contentDescription = stringResource(R.string.preview_cd_fullscreen)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            if (showConsole && running) {
                ConsoleScreen(
                    messages = state.consoleMessages,
                    onClearClick = onClearConsoleClick,
                    onCloseClick = { showConsole = false },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                content(Modifier.fillMaxSize())
                PreviewStatusOverlay(state, Modifier.align(Alignment.Center))
                PreviewErrorBanner(state, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/** ERROR 轻提示横幅（详情进 ConsoleScreen），RUNNING 且有 ERROR 时显示。 */
@Composable
private fun PreviewErrorBanner(state: PreviewUiState, modifier: Modifier = Modifier) {
    if (state.status != PreviewStatus.RUNNING || state.errorMessages.isEmpty()) return
    val spacing = MaterialTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.shapes.small
            )
            .padding(spacing.sm)
    ) {
        Text(
            text = stringResource(R.string.preview_page_errors, state.errorMessages.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun PreviewStatusOverlay(state: PreviewUiState, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    when (state.status) {
        PreviewStatus.STARTING,
        PreviewStatus.STOPPING -> CircularProgressIndicator(modifier)
        PreviewStatus.STOPPED -> Text(
            text = stringResource(R.string.preview_stopped),
            modifier = modifier,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PreviewStatus.FAILED -> Column(
            modifier = modifier.padding(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.preview_failed),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = state.errorMessage.orEmpty(),
                modifier = Modifier.padding(top = spacing.xs),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PreviewStatus.RUNNING -> Unit
    }
}

private const val MAX_VISIBLE_PAGE_ERRORS = 3
