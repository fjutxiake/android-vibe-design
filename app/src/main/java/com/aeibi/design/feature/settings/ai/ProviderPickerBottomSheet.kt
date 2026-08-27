package com.aeibi.design.feature.settings.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aeibi.design.R
import com.aeibi.design.ai.provider.ProviderDefinition
import com.aeibi.design.theme.spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderPickerBottomSheet(
    providers: List<ProviderDefinition>,
    onDismiss: () -> Unit,
    onSelect: (ProviderDefinition) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val spacing = MaterialTheme.spacing

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                text = stringResource(R.string.ai_add_service),
                modifier = Modifier.padding(horizontal = spacing.xs),
                fontWeight = FontWeight.SemiBold
            )
            providers.forEach { provider ->
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            scope.launch {
                                sheetState.hide()
                                onSelect(provider)
                            }
                        },
                    headlineContent = { Text(provider.displayName) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(provider.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(
                                R.string.ai_cd_select_provider,
                                provider.displayName
                            )
                        )
                    }
                )
            }
        }
    }
}
