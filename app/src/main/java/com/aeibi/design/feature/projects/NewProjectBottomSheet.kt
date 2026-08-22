package com.aeibi.design.feature.projects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import com.aeibi.design.theme.dimensions
import com.aeibi.design.theme.spacing
import com.aeibi.design.theme.systemAppIconShape

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NewProjectBottomSheet(onDismiss: () -> Unit) {
  var name by rememberSaveable { mutableStateOf("") }
  var description by rememberSaveable { mutableStateOf("") }
  var iconUri by rememberSaveable { mutableStateOf<String?>(null) }
  val spacing = MaterialTheme.spacing
  val dimensions = MaterialTheme.dimensions
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val photoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      iconUri = uri?.toString()
    }
  val appIconShape = systemAppIconShape()

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg),
      verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
      Text(
        text = "新建项目",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier
            .size(dimensions.projectPickerIcon)
            .clip(appIconShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable {
              photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
              )
            }
            .testTag("pick_project_icon_button"),
      ) {
        if (iconUri == null) {
          Icon(
            imageVector = Icons.Filled.AddPhotoAlternate,
            contentDescription = "选择应用图标",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(dimensions.iconLarge),
          )
        } else {
          AsyncImage(
            model = iconUri,
            contentDescription = "已选择的应用图标",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
          )
        }
      }
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        modifier = Modifier.fillMaxWidth().testTag("project_name_input"),
        label = { Text("名称") },
        placeholder = { Text("例如：周末去哪") },
        singleLine = true,
      )
      OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        modifier = Modifier.fillMaxWidth().testTag("project_description_input"),
        label = { Text("描述") },
        placeholder = { Text("用一句话说明这个 App") },
        minLines = 3,
        maxLines = 4,
      )
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = spacing.sm, bottom = spacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        TextButton(onClick = onDismiss) { Text("取消") }
        Button(onClick = onDismiss, enabled = name.isNotBlank()) { Text("创建项目") }
      }
    }
  }
}
