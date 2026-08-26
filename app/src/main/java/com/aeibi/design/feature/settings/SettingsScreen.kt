package com.aeibi.design.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeibi.design.R
import com.aeibi.design.feature.settings.language.currentLanguageDisplayName
import com.aeibi.design.theme.VibeDesignTheme
import com.aeibi.design.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onAiProvidersClick: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
    onTestApkEditorClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreenContent(
        modifier = modifier,
        aiProviderCount = uiState.aiProviderCount,
        onBackClick = onBackClick,
        onAiProvidersClick = onAiProvidersClick,
        onLanguageClick = onLanguageClick,
        onTestApkEditorClick = onTestApkEditorClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    modifier: Modifier = Modifier,
    aiProviderCount: Int = 0,
    onBackClick: () -> Unit = {},
    onAiProvidersClick: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
    onTestApkEditorClick: () -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    val aiProvidersSummary = if (aiProviderCount == 0) {
        stringResource(R.string.ai_providers_not_configured)
    } else {
        stringResource(R.string.ai_providers_configured, aiProviderCount)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.xs)
        ) {
            item {
                SettingsSectionTitle(stringResource(R.string.general_section_title))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    SettingsNavigationRow(
                        title = stringResource(R.string.language_section_title),
                        summary = currentLanguageDisplayName(),
                        onClick = onLanguageClick,
                        leadingIcon = Icons.Default.Translate
                    )
                }
            }
            item {
                SettingsSectionTitle(stringResource(R.string.ai_section_title))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    SettingsNavigationRow(
                        title = stringResource(R.string.ai_services_row),
                        summary = aiProvidersSummary,
                        onClick = onAiProvidersClick,
                        leadingIcon = Icons.Default.Hub
                    )
                }
            }
            item {
                SettingsSectionTitle("开发")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    SettingsNavigationRow(
                        title = "APK 编辑器（测试）",
                        summary = "调试 APK 手术链路，正式版移除",
                        onClick = onTestApkEditorClick,
                        leadingIcon = Icons.Default.Build
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    val spacing = MaterialTheme.spacing
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.xs),
        style = MaterialTheme.typography.labelLarge
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    VibeDesignTheme(dynamicColor = false) {
        SettingsScreenContent(aiProviderCount = 2)
    }
}

@Composable
fun SettingsNavigationRow(title: String, summary: String, onClick: () -> Unit, leadingIcon: ImageVector) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        leadingContent = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(summary) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.cd_open, title)
            )
        }
    )
}
