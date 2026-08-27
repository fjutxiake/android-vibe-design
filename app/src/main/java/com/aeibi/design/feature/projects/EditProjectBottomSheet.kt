package com.aeibi.design.feature.projects

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.aeibi.design.R
import com.aeibi.design.data.projects.Project
import com.aeibi.design.theme.spacing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EditProjectBottomSheet(
    project: Project,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, iconUri: String?) -> Unit,
    onDelete: () -> Unit,
    @StringRes errorMessage: Int? = null,
    submitting: Boolean = false
) {
    var name by rememberSaveable(project.id) { mutableStateOf(project.name) }
    var description by rememberSaveable(project.id) { mutableStateOf(project.description) }
    var pickedIconUri by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var iconError by rememberSaveable(project.id) { mutableStateOf<Int?>(null) }
    var showDeleteConfirmation by rememberSaveable(project.id) { mutableStateOf(false) }
    val spacing = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Text(
                text = stringResource(R.string.projects_edit_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            ProjectIconPicker(
                iconUri = pickedIconUri ?: project.iconUri,
                onIconPicked = {
                    pickedIconUri = it
                    iconError = null
                },
                onCropError = { iconError = R.string.projects_icon_crop_failed }
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().testTag("edit_project_name_input"),
                label = { Text(stringResource(R.string.name_label)) },
                singleLine = true
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth().testTag("edit_project_description_input"),
                label = { Text(stringResource(R.string.description_label)) },
                minLines = 3,
                maxLines = 4
            )
            val visibleError = errorMessage ?: iconError
            if (visibleError != null) {
                Text(
                    text = stringResource(visibleError),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("edit_project_error")
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.sm, bottom = spacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { showDeleteConfirmation = true },
                    enabled = !submitting,
                    modifier = Modifier.testTag("delete_project_button")
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    TextButton(onClick = onDismiss, enabled = !submitting) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = { onSave(name, description, pickedIconUri) },
                        enabled = name.isNotBlank() && !submitting
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.delete_project_title)) },
            text = { Text(stringResource(R.string.delete_project_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("confirm_delete_project_button")
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
