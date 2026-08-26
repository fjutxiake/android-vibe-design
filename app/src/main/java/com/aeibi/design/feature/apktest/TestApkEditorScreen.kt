package com.aeibi.design.feature.apktest

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeibi.design.theme.spacing

/**
 * 临时调试界面：手动触发 APK 手术链路。
 * 正式版移除。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TestApkEditorScreen(modifier: Modifier = Modifier, onBackClick: () -> Unit = {}) {
    val spacing = MaterialTheme.spacing
    val viewModel: TestApkEditorViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val templatePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                viewModel.selectTemplate(uri)
            } else {
                viewModel.onTemplatePickCancelled()
            }
        }
    val logPath by viewModel.logPath.collectAsStateWithLifecycle()

    val frontendPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                viewModel.selectFrontend(uri)
            }
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("APK 编辑器（测试）") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item {
                OutlinedButton(
                    onClick = { templatePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isBuilding
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Text(uiState.templateName ?: "选择模板 APK（任意 APK）")
                }
            }
            item {
                Text(
                    text = "模板状态: ${uiState.templatePath ?: "未选择（选择后此处置为路径）"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedButton(
                    onClick = { frontendPicker.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isBuilding
                ) {
                    Text(uiState.frontendName ?: "选择前端代码目录（注入 assets/frontend_app/）")
                }
            }
            uiState.frontendPath?.let { path ->
                item {
                    Text(
                        text = "前端目录: $path",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = viewModel::previewStructure,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.templatePath != null && !uiState.isBuilding && !uiState.isPreviewingStructure
                ) {
                    Text(if (uiState.isPreviewingStructure) "解码中…" else "查看文件结构")
                }
            }
            uiState.structure?.let { structure ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        HorizontalDivider()
                        Text(
                            text = "模板文件结构（${structure.size} 行）",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                items(structure) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            logPath?.let { path ->
                item {
                    Text(
                        text = "外部日志: $path",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = uiState.packageName,
                    onValueChange = viewModel::updatePackageName,
                    label = { Text("包名") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isBuilding,
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.appLabel,
                    onValueChange = viewModel::updateAppLabel,
                    label = { Text("应用名") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isBuilding,
                    singleLine = true
                )
            }
            item {
                Button(
                    onClick = viewModel::build,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.templatePath != null && !uiState.isBuilding
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(if (uiState.isBuilding) "构建中…" else "开始构建")
                }
            }
            uiState.error?.let { error ->
                item {
                    Text(text = error, color = MaterialTheme.colorScheme.error)
                }
            }
            uiState.result?.let { result ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        HorizontalDivider()
                        Text(text = result, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Text(
                    text = "构建日志（${uiState.logs.size} 条）",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            items(uiState.logs) { log ->
                Text(
                    text = log,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
