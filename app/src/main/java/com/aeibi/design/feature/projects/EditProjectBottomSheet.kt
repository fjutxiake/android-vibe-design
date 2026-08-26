package com.aeibi.design.feature.projects

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
import androidx.compose.ui.text.font.FontWeight
import com.aeibi.design.data.projects.Project
import com.aeibi.design.theme.spacing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EditProjectBottomSheet(
    project: Project,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, iconUri: String?) -> Unit,
    onDelete: () -> Unit,
    errorMessage: String? = null,
    submitting: Boolean = false
) {
    var name by rememberSaveable(project.id) { mutableStateOf(project.name) }
    var description by rememberSaveable(project.id) { mutableStateOf(project.description) }
    var pickedIconUri by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
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
                text = "编辑项目",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            ProjectIconPicker(
                iconUri = pickedIconUri ?: project.iconUri,
                onIconPicked = { pickedIconUri = it }
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().testTag("edit_project_name_input"),
                label = { Text("名称") },
                singleLine = true
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth().testTag("edit_project_description_input"),
                label = { Text("描述") },
                minLines = 3,
                maxLines = 4
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
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
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") }
                    Button(
                        onClick = { onSave(name, description, pickedIconUri) },
                        enabled = name.isNotBlank() && !submitting
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("删除项目") },
            text = { Text("将删除该项目及其全部会话,此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("confirm_delete_project_button")
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("取消") }
            }
        )
    }
}
