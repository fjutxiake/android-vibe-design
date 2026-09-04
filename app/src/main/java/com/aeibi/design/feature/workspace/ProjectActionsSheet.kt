package com.aeibi.design.feature.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aeibi.design.R

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProjectActionsSheet(
    onDismiss: () -> Unit,
    onBuildClick: () -> Unit,
    onVersionsClick: () -> Unit,
    onExportClick: () -> Unit,
    onProjectSettingsClick: () -> Unit,
    onAppSettingsClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.workspace_action_build)) },
                leadingContent = { Icon(Icons.Filled.Build, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onBuildClick()
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.workspace_action_versions)) },
                leadingContent = { Icon(Icons.Filled.History, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onVersionsClick()
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.workspace_action_export)) },
                leadingContent = { Icon(Icons.Filled.Save, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onExportClick()
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.workspace_action_project_settings)) },
                leadingContent = { Icon(Icons.Filled.Tune, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onProjectSettingsClick()
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.workspace_action_app_settings)) },
                leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onAppSettingsClick()
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.delete_project_title)) },
                leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                colors = ListItemDefaults.colors(
                    headlineColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.clickable {
                    onDismiss()
                    onDeleteClick()
                }
            )
        }
    }
}
