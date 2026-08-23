package com.aeibi.design.feature.build

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aeibi.design.theme.spacing

@Composable
fun BuildLog(modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing

    Text(
        text = "暂无构建记录",
        modifier = modifier.fillMaxWidth().padding(vertical = spacing.lg),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
