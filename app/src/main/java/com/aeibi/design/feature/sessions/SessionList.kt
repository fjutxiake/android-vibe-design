package com.aeibi.design.feature.sessions

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.aeibi.design.R
import com.aeibi.design.data.sessions.SessionEntity
import com.aeibi.design.theme.spacing

@Composable
fun SessionList(
    state: LazyListState,
    sessions: List<SessionEntity>,
    selectedSessionId: String?,
    onSessionSelected: (String) -> Unit,
    onSessionRename: (SessionEntity) -> Unit,
    onSessionDelete: (SessionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val density = LocalDensity.current
    var menuSessionId by remember { mutableStateOf<String?>(null) }
    var menuPosition by remember { mutableStateOf(Offset.Zero) }

    if (sessions.isEmpty()) {
        Text(
            text = stringResource(R.string.no_sessions),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth().padding(vertical = spacing.lg)
        )
        return
    }

    LazyColumn(
        state = state,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs)
    ) {
        items(sessions, key = { it.id }) { session ->
            val isSelected = session.id == selectedSessionId
            Box(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .pointerInput(session.id) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                menuPosition = down.position
                                waitForUpOrCancellation()
                            }
                        }
                        .combinedClickable(
                            onClick = { onSessionSelected(session.id) },
                            onLongClick = { menuSessionId = session.id }
                        ),
                    colors = ListItemDefaults.colors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        headlineColor = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ),
                    headlineContent = { Text(session.title) }
                )
                Box(modifier = Modifier.align(Alignment.TopStart)) {
                    DropdownMenu(
                        expanded = menuSessionId == session.id,
                        onDismissRequest = { menuSessionId = null },
                        modifier = Modifier.width(180.dp),
                        offset = with(density) {
                            DpOffset(menuPosition.x.toDp(), menuPosition.y.toDp())
                        },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.session_actions_rename)) },
                            trailingIcon = {
                                Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                            },
                            onClick = {
                                menuSessionId = null
                                onSessionRename(session)
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = spacing.sm),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.session_actions_delete)) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                            onClick = {
                                menuSessionId = null
                                onSessionDelete(session)
                            }
                        )
                    }
                }
            }
        }
    }
}
