package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.aeibi.design.R
import com.aeibi.design.theme.spacing

@Composable
fun ChatComposer(
    input: String,
    isRunning: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier.fillMaxWidth().imePadding().padding(spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            enabled = !isRunning,
            placeholder = { Text(stringResource(R.string.chat_input_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank() && !isRunning) onSend() })
        )
        IconButton(
            onClick = if (isRunning) onCancel else onSend,
            enabled = isRunning || input.isNotBlank()
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
