package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aeibi.design.R
import com.aeibi.design.theme.spacing

@Composable
fun ChatComposer(modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier.fillMaxWidth().imePadding().padding(spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.chat_cd_add_tool)
            )
        }
        OutlinedTextField(
            value = "",
            onValueChange = {},
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.chat_input_hint)) }
        )
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.chat_cd_send)
            )
        }
    }
}
