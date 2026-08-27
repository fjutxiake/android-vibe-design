package com.aeibi.design.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aeibi.design.R
import com.aeibi.design.theme.spacing

@Composable
fun ChatEmptyState(projectId: String, sessionId: String?, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = modifier.padding(spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text =
            if (sessionId == null) {
                stringResource(R.string.chat_empty_no_session, projectId)
            } else {
                stringResource(R.string.chat_empty_session, projectId, sessionId)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
