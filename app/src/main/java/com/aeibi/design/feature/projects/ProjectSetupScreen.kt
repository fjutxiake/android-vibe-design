package com.aeibi.design.feature.projects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aeibi.design.R
import com.aeibi.design.theme.spacing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProjectSetupScreen(
    onBackClick: () -> Unit,
    onBrowseTemplatesClick: () -> Unit,
    onStartBlankClick: (onResult: (Result<Unit>) -> Unit) -> Unit,
    onImportTemplateClick: (uri: android.net.Uri, onResult: (Result<Unit>) -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    var isStartingBlank by rememberSaveable { mutableStateOf(false) }
    var isImporting by rememberSaveable { mutableStateOf(false) }
    var startFailed by rememberSaveable { mutableStateOf(false) }
    val busy = isStartingBlank || isImporting

    val importPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            startFailed = false
            onImportTemplateClick(uri) { result ->
                isImporting = false
                startFailed = result.isFailure
            }
        }
    }

    BackHandler(enabled = busy) {}

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.project_setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick, enabled = !isStartingBlank) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            ElevatedCard(
                onClick = onBrowseTemplatesClick,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("browse_templates")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GridView,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.project_setup_template_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.project_setup_custom_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (startFailed) {
                Text(
                    text = stringResource(R.string.project_setup_start_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(
                onClick = {
                    isStartingBlank = true
                    startFailed = false
                    onStartBlankClick { result ->
                        isStartingBlank = false
                        startFailed = result.isFailure
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("start_blank")
            ) {
                if (isStartingBlank) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.project_setup_start_blank))
                }
            }
            TextButton(
                onClick = {
                    importPicker.launch(IMPORT_MIME_TYPES)
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("import_template")
            ) {
                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.project_setup_import_template))
                }
            }
        }
    }
}

private val IMPORT_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream"
)
