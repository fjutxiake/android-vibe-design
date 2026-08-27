package com.aeibi.design.feature.settings.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aeibi.design.R
import com.aeibi.design.ai.provider.ProviderConfig
import com.aeibi.design.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConfigEditor(
    initialConfig: ProviderConfig,
    providerName: String,
    providerIconRes: Int?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onRevealApiKey: suspend () -> String?,
    onSave: (ProviderConfig, String) -> Unit
) {
    val spacing = MaterialTheme.spacing
    var displayName by remember(initialConfig.id) { mutableStateOf(initialConfig.displayName) }
    var endpoint by remember(initialConfig.id) { mutableStateOf(initialConfig.endpoint) }
    var models by remember(initialConfig.id) { mutableStateOf(initialConfig.models.ifEmpty { listOf("") }) }
    var apiKey by remember(initialConfig.id) { mutableStateOf("") }
    var isApiKeyLoading by remember(initialConfig.id) { mutableStateOf(true) }
    var apiKeyVisible by remember(initialConfig.id) { mutableStateOf(false) }
    var showCustomSettings by remember(initialConfig.id) {
        mutableStateOf(initialConfig.endpoint.isBlank() || initialConfig.models.none { it.isNotBlank() })
    }

    LaunchedEffect(initialConfig.id) {
        isApiKeyLoading = true
        try {
            apiKey = onRevealApiKey().orEmpty()
        } finally {
            isApiKeyLoading = false
        }
    }

    val normalizedModels = models.map(String::trim).filter(String::isNotEmpty).distinct()
    val draft = initialConfig.copy(displayName = displayName, endpoint = endpoint, models = normalizedModels)
    val formComplete = displayName.isNotBlank() && endpoint.isNotBlank() && normalizedModels.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.md)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                if (providerIconRes != null) {
                    Icon(
                        painter = painterResource(providerIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = Color.Unspecified
                    )
                } else {
                    Icon(Icons.Default.Hub, contentDescription = null)
                }
                Text(providerName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.name_label)) },
                singleLine = true
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.ai_api_key_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(
                                if (apiKeyVisible) R.string.ai_cd_hide_key else R.string.ai_cd_show_key
                            )
                        )
                    }
                },
                singleLine = true
            )

            TextButton(onClick = { showCustomSettings = !showCustomSettings }) {
                Icon(
                    imageVector = if (showCustomSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(spacing.xs))
                Text(stringResource(R.string.ai_custom_settings))
            }

            if (showCustomSettings) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ai_endpoint_label)) },
                    singleLine = true
                )

                Text(
                    stringResource(R.string.ai_models_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                models.forEachIndexed { index, model ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = model,
                            onValueChange = { value -> models = models.toMutableList().also { it[index] = value } },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    if (models.size == 1) {
                                        stringResource(R.string.ai_model_name_label)
                                    } else {
                                        stringResource(R.string.ai_model_number_label, index + 1)
                                    }
                                )
                            },
                            singleLine = true
                        )
                        IconButton(onClick = {
                            models = if (models.size == 1) {
                                listOf("")
                            } else {
                                models.filterIndexed { modelIndex, _ -> modelIndex != index }
                            }
                        }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.ai_cd_remove_model)
                            )
                        }
                    }
                }
                TextButton(onClick = { models = models + "" }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(spacing.xs))
                    Text(stringResource(R.string.ai_add_model))
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = formComplete && !isSaving && !isApiKeyLoading,
                onClick = { onSave(draft, apiKey) }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
