package com.aeibi.design.feature.sessions

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.aeibi.design.data.sessions.SessionEntity
import com.aeibi.design.theme.spacing

@Composable
fun SessionList(
    sessions: List<SessionEntity>,
    selectedSessionId: String?,
    onSessionSelected: (String) -> Unit,
    onSessionLongClick: (SessionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing

    if (sessions.isEmpty()) {
        Text(
            text = "暂无会话",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth().padding(vertical = spacing.lg)
        )
        return
    }

    sessions.forEach { session ->
        ListItem(
            headlineContent = { Text(session.title) },
            supportingContent = { if (session.id == selectedSessionId) Text("当前会话") },
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onSessionSelected(session.id) },
                    onLongClick = { onSessionLongClick(session) }
                )
        )
    }
}
