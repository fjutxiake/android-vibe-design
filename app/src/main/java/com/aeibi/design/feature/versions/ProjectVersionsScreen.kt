package com.aeibi.design.feature.versions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aeibi.design.R
import com.aeibi.design.data.versions.VersionSnapshot
import com.aeibi.design.data.versions.VersionTrigger
import com.aeibi.design.theme.spacing
import java.text.DateFormat
import java.util.Date

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProjectVersionsScreen(projectId: String, modifier: Modifier = Modifier, onBackClick: () -> Unit = {}) {
    val viewModel: ProjectVersionsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    var pendingRestore by remember { mutableStateOf<VersionSnapshot?>(null) }
    val timeFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val snapshotLabel = stringResource(R.string.versions_manual_snapshot)

    LaunchedEffect(projectId) { viewModel.load(projectId) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.versions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            !state.ready -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.versions_not_ready)) }

            else -> Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(MaterialTheme.spacing.md)
            ) {
                if (state.message != null) {
                    Text(
                        text = state.message.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = { viewModel.createSnapshot(snapshotLabel) },
                    enabled = !state.busy
                ) { Text(stringResource(R.string.versions_create_snapshot)) }

                if (state.versions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.versions_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(state.versions, key = { it.id }) { version ->
                            ListItem(
                                headlineContent = {
                                    Text(version.label.ifEmpty { version.id.take(SHORT_OID_LENGTH) })
                                },
                                supportingContent = {
                                    Text(
                                        triggerLabel(version.trigger) +
                                            " · " +
                                            timeFormat.format(Date(version.createdAt)) +
                                            " · " +
                                            version.id.take(SHORT_OID_LENGTH)
                                    )
                                },
                                trailingContent = {
                                    TextButton(
                                        onClick = { pendingRestore = version },
                                        enabled = !state.busy
                                    ) { Text(stringResource(R.string.versions_restore)) }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    pendingRestore?.let { version ->
        val restoreMessage = stringResource(
            R.string.versions_restore_commit_message,
            version.id.take(SHORT_OID_LENGTH)
        )
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.versions_restore_confirm_title)) },
            text = { Text(stringResource(R.string.versions_restore_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restore(version.id, restoreMessage)
                    pendingRestore = null
                }) { Text(stringResource(R.string.versions_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) {
                    Text(stringResource(R.string.versions_cancel))
                }
            }
        )
    }
}

@Composable
private fun triggerLabel(trigger: VersionTrigger): String = when (trigger) {
    VersionTrigger.INIT -> stringResource(R.string.versions_trigger_init)
    VersionTrigger.AUTO_BUILD -> stringResource(R.string.versions_trigger_auto_build)
    VersionTrigger.MANUAL -> stringResource(R.string.versions_trigger_manual)
    VersionTrigger.RESTORE -> stringResource(R.string.versions_trigger_restore)
    VersionTrigger.PRE_RESTORE -> stringResource(R.string.versions_trigger_pre_restore)
}

private const val SHORT_OID_LENGTH = 7
