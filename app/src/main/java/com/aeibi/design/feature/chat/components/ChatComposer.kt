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
import com.aeibi.design.theme.spacing

@Composable
fun ChatComposer(modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier.fillMaxWidth().imePadding().padding(spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {}) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "添加工具")
        }
        OutlinedTextField(
            value = "",
            onValueChange = {},
            modifier = Modifier.weight(1f),
            placeholder = { Text("向 AI 描述你想构建的内容") }
        )
        IconButton(onClick = {}) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
        }
    }
}
