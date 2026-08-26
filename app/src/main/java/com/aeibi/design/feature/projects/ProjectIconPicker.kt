package com.aeibi.design.feature.projects

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.aeibi.design.theme.dimensions
import com.aeibi.design.theme.systemAppIconShape
import com.yalantis.ucrop.UCrop
import java.io.File
import java.util.UUID

@Composable
fun ProjectIconPicker(iconUri: String?, onIconPicked: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cropImage =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                UCrop.getOutput(result.data ?: return@rememberLauncherForActivityResult)
                    ?.let { onIconPicked(it.toString()) }
            }
        }
    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { source ->
                val destination =
                    File(context.cacheDir, "project-icon-${UUID.randomUUID()}.png").toUri()
                UCrop.of(source, destination)
                    .withAspectRatio(1f, 1f)
                    .withMaxResultSize(512, 512)
                    .withOptions(UCrop.Options().apply { setCompressionFormat(Bitmap.CompressFormat.PNG) })
                    .start(context, cropImage)
            }
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
        modifier
            .size(MaterialTheme.dimensions.projectPickerIcon)
            .clip(systemAppIconShape())
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            .testTag("pick_project_icon_button")
    ) {
        if (iconUri == null) {
            Icon(
                imageVector = Icons.Filled.AddPhotoAlternate,
                contentDescription = "选择应用图标",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(MaterialTheme.dimensions.iconLarge)
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(iconUri)
                    .build(),
                contentDescription = "已选择的应用图标",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
