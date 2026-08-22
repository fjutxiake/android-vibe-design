package com.aeibi.design.feature.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SessionList(
  selectedSessionId: String?,
  onSessionSelected: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val sessions = listOf("session-2" to "项目页面设计")

  sessions.forEach { (id, title) ->
    ListItem(
      headlineContent = { Text(title) },
      supportingContent = { if (id == selectedSessionId) Text("当前会话") },
      modifier = modifier.fillMaxWidth().clickable { onSessionSelected(id) },
    )
  }
}
