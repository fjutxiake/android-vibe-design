package com.aeibi.design.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.aeibi.design.theme.spacing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NewProjectBottomSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, iconUri: String?) -> Unit,
    errorMessage: String? = null,
    submitting: Boolean = false
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var iconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var iconError by rememberSaveable { mutableStateOf<String?>(null) }
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
                text = "新建项目",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            ProjectIconPicker(
                iconUri = iconUri,
                onIconPicked = {
                    iconUri = it
                    iconError = null
                },
                onCropError = { iconError = "图标裁剪失败，请重新选择" }
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().testTag("project_name_input"),
                label = { Text("名称") },
                placeholder = { Text("例如：周末去哪") },
                singleLine = true
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth().testTag("project_description_input"),
                label = { Text("描述") },
                placeholder = { Text("用一句话说明这个 App") },
                minLines = 3,
                maxLines = 4
            )
            val visibleError = errorMessage ?: iconError
            if (visibleError != null) {
                Text(
                    text = visibleError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("create_project_error")
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.sm, bottom = spacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(
                    onClick = { onCreate(name, description, iconUri) },
                    enabled = name.isNotBlank() && !submitting
                ) {
                    Text("创建项目")
                }
            }
        }
    }
}
