package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aeibi.design.R
import com.aeibi.design.feature.chat.SessionProviderUiState
import com.aeibi.design.theme.spacing
import kotlinx.coroutines.launch

/**
 * 聊天区顶部的会话级 provider 条:展示下一条消息将使用的 provider/model,
 * 点击打开 [SessionProviderSheet] 更换。无会话选中时由调用方隐藏。
 */
@Composable
fun SessionProviderBar(state: SessionProviderUiState, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val spacing = MaterialTheme.spacing
    val current = state.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.md, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Icon(
                painter = painterResource(current?.iconRes ?: R.drawable.provider_default),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape),
                tint = Color.Unspecified
            )
            Text(
                text = current?.let { "${it.displayName} · ${it.model}" }
                    ?: stringResource(R.string.chat_provider_none),
                style = MaterialTheme.typography.labelLarge,
                color = if (current == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.chat_cd_change_provider),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider()
    }
}

/**
 * 会话级 provider 选择面板:解除绑定(跟随全局默认)或换绑任一已配置
 * provider 的模型。缺 key 的配置禁选并提示,引导先去设置补 key。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionProviderSheet(
    state: SessionProviderUiState,
    onDismiss: () -> Unit,
    onSelect: (providerConfigId: String?, model: String?) -> Unit
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
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                text = stringResource(R.string.chat_provider_sheet_title),
                modifier = Modifier.padding(horizontal = spacing.xs),
                fontWeight = FontWeight.SemiBold
            )
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        scope.launch { sheetState.hide() }
                        onSelect(null, null)
                    },
                headlineContent = { Text(stringResource(R.string.chat_provider_follow_default)) },
                supportingContent = {
                    Text(
                        text = state.defaultSelection?.let { "${it.displayName} · ${it.model}" }
                            ?: stringResource(R.string.chat_provider_default_unset)
                    )
                },
                trailingContent = {
                    if (state.followsDefault) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
            if (state.options.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_provider_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(spacing.md)
                )
            }
            state.options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.xs, start = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Icon(
                        painter = painterResource(option.iconRes),
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape),
                        tint = Color.Unspecified
                    )
                    Text(
                        text = option.config.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                option.config.models.forEach { model ->
                    val selected = !state.followsDefault &&
                        state.current?.providerConfigId == option.config.id &&
                        state.current?.model == model
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .let { if (!option.hasApiKey) it.alpha(0.5f) else it }
                            .clickable(enabled = option.hasApiKey) {
                                scope.launch { sheetState.hide() }
                                onSelect(option.config.id, model)
                            },
                        headlineContent = { Text(model) },
                        supportingContent = if (!option.hasApiKey) {
                            {
                                Text(
                                    text = stringResource(R.string.chat_provider_missing_key),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            null
                        },
                        trailingContent = {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
