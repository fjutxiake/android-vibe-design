package com.aeibi.design.feature.settings.ai

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeibi.design.R
import com.aeibi.design.ai.provider.ProviderConfig
import com.aeibi.design.ai.provider.ProviderDefinition
import com.aeibi.design.theme.VibeDesignTheme
import com.aeibi.design.theme.spacing
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProvidersScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    viewModel: AiProvidersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AiProvidersContent(
        modifier = modifier,
        uiState = uiState,
        onBackClick = onBackClick,
        onClearFeedback = viewModel::clearFeedback,
        onRevealApiKey = viewModel::revealApiKey,
        onSaveProvider = viewModel::saveProvider,
        onDeleteProvider = viewModel::deleteProvider,
        onSelectModel = viewModel::selectModel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiProvidersContent(
    modifier: Modifier = Modifier,
    uiState: AiProvidersUiState,
    onBackClick: () -> Unit = {},
    onClearFeedback: () -> Unit = {},
    onRevealApiKey: suspend (String) -> String? = { null },
    onSaveProvider: (ProviderConfig, String, (Boolean) -> Unit) -> Unit = { _, _, onComplete -> onComplete(true) },
    onDeleteProvider: (String) -> Unit = {},
    onSelectModel: (String, String) -> Unit = { _, _ -> }
) {
    val spacing = MaterialTheme.spacing
    val context = LocalContext.current
    var editingProvider by remember { mutableStateOf<ProviderConfigItem?>(null) }
    var pendingDelete by remember { mutableStateOf<ProviderConfig?>(null) }
    var showProviderPicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.feedback) {
        uiState.feedback?.let { resId ->
            Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
            onClearFeedback()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_services_row)) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs)
        ) {
            if (uiState.configuredProviders.isEmpty()) {
                item { EmptyProviderState(onAdd = { showProviderPicker = true }) }
            } else {
                item {
                    ProviderGroup(
                        providers = uiState.configuredProviders,
                        providerIconRes = { item ->
                            uiState.providerDefinitions
                                .firstOrNull { it.type == item.config.providerType }
                                ?.iconRes
                                ?: R.drawable.provider_default
                        },
                        onEdit = {
                            onClearFeedback()
                            editingProvider = it
                        },
                        onDelete = { pendingDelete = it.config },
                        onSelectModel = onSelectModel,
                        onAdd = { showProviderPicker = true }
                    )
                }
            }
        }
    }

    if (showProviderPicker) {
        ProviderPickerBottomSheet(
            providers = uiState.providerDefinitions,
            onDismiss = { showProviderPicker = false },
            onSelect = { provider ->
                showProviderPicker = false
                onClearFeedback()
                editingProvider = ProviderConfigItem(
                    config = ProviderConfig(
                        id = UUID.randomUUID().toString(),
                        providerType = provider.type,
                        displayName = provider.displayName,
                        endpoint = provider.defaultEndpoint,
                        models = provider.defaultModels.ifEmpty { listOf("") }
                    )
                )
            }
        )
    }

    editingProvider?.let { item ->
        val definition = uiState.providerDefinitions.firstOrNull { it.type == item.config.providerType }
        ProviderConfigEditor(
            initialConfig = item.config,
            providerName = definition?.displayName ?: item.config.providerType,
            providerIconRes = definition?.iconRes,
            isSaving = uiState.isSaving,
            onDismiss = { editingProvider = null },
            onRevealApiKey = { onRevealApiKey(item.config.id) },
            onSave = { config, apiKey ->
                onSaveProvider(config, apiKey) { saved -> if (saved) editingProvider = null }
            }
        )
    }

    pendingDelete?.let { config ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.ai_remove_provider_title, config.displayName)) },
            text = { Text(stringResource(R.string.ai_remove_provider_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteProvider(config.id)
                    pendingDelete = null
                }) {
                    Text(
                        stringResource(R.string.ai_remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun AiProvidersEmptyScreenPreview() {
    VibeDesignTheme(dynamicColor = false) {
        AiProvidersContent(uiState = AiProvidersUiState())
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun AiProvidersConfiguredScreenPreview() {
    val previewProviderDefinitions = listOf(
        ProviderDefinition(
            type = "openai",
            displayName = "OpenAI",
            iconRes = R.drawable.provider_openai,
            defaultEndpoint = "https://api.openai.com/v1",
            defaultModels = listOf("gpt-5.6-sol")
        ),
        ProviderDefinition(
            type = "deepseek",
            displayName = "DeepSeek",
            iconRes = R.drawable.provider_deepseek,
            defaultEndpoint = "https://api.deepseek.com",
            defaultModels = listOf("deepseek-v4-flash")
        )
    )
    val previewProviderItems = listOf(
        ProviderConfigItem(
            config = ProviderConfig(
                id = "openai",
                providerType = "openai",
                displayName = "OpenAI 个人账号",
                endpoint = "https://api.openai.com/v1",
                models = listOf("gpt-5.6-sol", "gpt-5.6-terra")
            )
        ),
        ProviderConfigItem(
            config = ProviderConfig(
                id = "deepseek",
                providerType = "deepseek",
                displayName = "DeepSeek",
                endpoint = "https://api.deepseek.com",
                models = listOf("deepseek-v4-flash")
            )
        )
    )
    VibeDesignTheme(dynamicColor = false) {
        AiProvidersContent(
            uiState = AiProvidersUiState(
                configuredProviders = previewProviderItems,
                providerDefinitions = previewProviderDefinitions
            )
        )
    }
}

@Composable
private fun ProviderGroup(
    providers: List<ProviderConfigItem>,
    providerIconRes: (ProviderConfigItem) -> Int,
    onEdit: (ProviderConfigItem) -> Unit,
    onDelete: (ProviderConfigItem) -> Unit,
    onSelectModel: (String, String) -> Unit,
    onAdd: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            providers.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(start = spacing.md))
                ProviderRow(
                    item = item,
                    providerIconRes = providerIconRes(item),
                    onClick = { onEdit(item) },
                    onDelete = { onDelete(item) }
                )
                item.config.models.forEach { modelId ->
                    ListItem(
                        modifier = Modifier.clickable { onSelectModel(item.config.id, modelId) },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        headlineContent = { Text(modelId, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            RadioButton(
                                selected = item.selectedModelId == modelId,
                                onClick = { onSelectModel(item.config.id, modelId) }
                            )
                        }
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(start = spacing.md))
            ListItem(
                modifier = Modifier.clickable(onClick = onAdd),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                leadingContent = {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(spacing.xs))
                    }
                },
                headlineContent = {
                    Text(
                        stringResource(R.string.ai_add_service),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
    }
}

@Composable
private fun ProviderRow(item: ProviderConfigItem, providerIconRes: Int, onClick: () -> Unit, onDelete: () -> Unit) {
    val modelSummary = item.config.models.joinToString()

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        headlineContent = {
            Text(
                text = item.config.displayName,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = modelSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(providerIconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(
                        R.string.ai_cd_remove_provider,
                        item.config.displayName
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun EmptyProviderState(onAdd: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Icon(
            Icons.Default.Hub,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.ai_no_providers),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onAdd) { Text(stringResource(R.string.ai_add_service)) }
    }
}
