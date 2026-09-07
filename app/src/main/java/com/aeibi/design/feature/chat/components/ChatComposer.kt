package com.aeibi.design.feature.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.aeibi.design.R
import com.aeibi.design.feature.chat.PendingAttachment
import com.aeibi.design.theme.spacing

@Composable
fun ChatComposer(
    input: String,
    attachment: PendingAttachment?,
    isRunning: Boolean,
    onInputChange: (String) -> Unit,
    onRemoveAttachment: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth().imePadding().padding(spacing.xs)
    ) {
        attachment?.let { AttachmentBar(it, onRemoveAttachment) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                enabled = !isRunning,
                placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!isRunning) onSend() })
            )
            IconButton(
                onClick = if (isRunning) onCancel else onSend,
                enabled = isRunning || input.isNotBlank() || attachment != null
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(
                        if (isRunning) R.string.chat_cd_cancel else R.string.chat_cd_send
                    )
                )
            }
        }
    }
}

/** 待发送附件折叠条——标题行可展开正文，x 移除。 */
@Composable
private fun AttachmentBar(attachment: PendingAttachment, onRemove: () -> Unit) {
    val spacing = MaterialTheme.spacing
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.xs)
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = !expanded }
                    .padding(start = spacing.md, top = spacing.xs, bottom = spacing.xs)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = attachment.title,
                    modifier = Modifier.weight(1f).padding(horizontal = spacing.xs),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
                IconButton(onClick = onRemove, modifier = Modifier.padding(end = spacing.xs)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.chat_cd_remove_attachment),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (expanded) {
                Text(
                    text = attachment.body,
                    modifier = Modifier.padding(start = spacing.md, end = spacing.md, bottom = spacing.sm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
