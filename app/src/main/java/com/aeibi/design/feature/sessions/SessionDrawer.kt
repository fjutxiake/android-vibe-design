package com.aeibi.design.feature.sessions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aeibi.design.theme.spacing

@Composable
fun SessionDrawer(
  selectedSessionId: String?,
  onNewChatClick: () -> Unit,
  onSessionSelected: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val spacing = MaterialTheme.spacing

  ModalDrawerSheet(modifier = modifier.fillMaxWidth(0.86f).fillMaxHeight()) {
    Column(modifier = Modifier.padding(spacing.md)) {
      Button(
        onClick = onNewChatClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm),
      ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
        Text("新建会话")
      }
      SessionList(
        selectedSessionId = selectedSessionId,
        onSessionSelected = onSessionSelected,
      )
    }
  }
}
