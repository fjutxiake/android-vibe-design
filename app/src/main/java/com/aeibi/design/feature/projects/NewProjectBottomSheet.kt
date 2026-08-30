package com.aeibi.design.feature.projects

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.aeibi.design.R
import com.aeibi.design.theme.spacing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NewProjectBottomSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, iconUri: String?) -> Unit,
    @StringRes errorMessage: Int? = null,
    submitting: Boolean = false
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var iconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var iconError by rememberSaveable { mutableStateOf<Int?>(null) }
    val spacing = MaterialTheme.spacing
    val currentSubmitting by rememberUpdatedState(submitting)
    val confirmSheetValueChange: (SheetValue) -> Boolean = remember {
        { value -> value != SheetValue.Hidden || !currentSubmitting }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = confirmSheetValueChange
    )

    ModalBottomSheet(
        onDismissRequest = { if (!submitting) onDismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = !submitting,
        containerColor = MaterialTheme.colorScheme.surface,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !submitting,
            shouldDismissOnClickOutside = !submitting
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Text(
                text = stringResource(R.string.projects_new_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            ProjectIconPicker(
                iconUri = iconUri,
                onIconPicked = {
                    iconUri = it
                    iconError = null
                },
                onCropError = { iconError = R.string.projects_icon_crop_failed }
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().testTag("project_name_input"),
                label = { Text(stringResource(R.string.name_label)) },
                placeholder = { Text(stringResource(R.string.projects_name_placeholder)) },
                singleLine = true
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth().testTag("project_description_input"),
                label = { Text(stringResource(R.string.description_label)) },
                placeholder = { Text(stringResource(R.string.projects_description_placeholder)) },
                minLines = 3,
                maxLines = 4
            )
            val visibleError = errorMessage ?: iconError
            if (visibleError != null) {
                Text(
                    text = stringResource(visibleError),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("create_project_error")
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.sm, bottom = spacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onDismiss, enabled = !submitting) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { onCreate(name, description, iconUri) },
                    enabled = name.isNotBlank() && !submitting
                ) {
                    Text(stringResource(R.string.projects_create_button))
                }
            }
        }
    }
}
