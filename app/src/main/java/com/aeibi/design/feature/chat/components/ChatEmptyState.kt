package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aeibi.design.theme.spacing

@Composable
fun ChatEmptyState(projectId: String, sessionId: String?, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = modifier.padding(spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "开始新的对话", style = MaterialTheme.typography.headlineSmall)
        Text(
            text =
            if (sessionId == null) {
                "项目 $projectId · 尚未创建会话"
            } else {
                "项目 $projectId · 会话 $sessionId"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
