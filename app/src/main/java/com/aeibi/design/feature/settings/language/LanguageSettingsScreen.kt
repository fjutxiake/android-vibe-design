package com.aeibi.design.feature.settings.language

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.aeibi.design.R
import com.aeibi.design.i18n.AppLanguage
import com.aeibi.design.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen(modifier: Modifier = Modifier, onBackClick: () -> Unit = {}) {
    val configuration = LocalConfiguration.current
    var language by remember(configuration) { mutableStateOf(AppLanguage.current()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.language_section_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
            contentPadding = PaddingValues(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.xs
            )
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column {
                        AppLanguage.entries.forEach { option ->
                            LanguageRow(
                                title = option.displayName(),
                                selected = language == option,
                                onClick = {
                                    language = option
                                    option.setAsCurrent()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun currentLanguageDisplayName(): String {
    val configuration = LocalConfiguration.current
    val language = remember(configuration) { AppLanguage.current() }
    return language.displayName()
}

@Composable
private fun AppLanguage.displayName(): String = when (this) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_follow_system)
    AppLanguage.SIMPLIFIED_CHINESE -> stringResource(R.string.language_zh)
    AppLanguage.ENGLISH -> stringResource(R.string.language_en)
}

@Composable
private fun LanguageRow(title: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        trailingContent = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}
