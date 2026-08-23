package com.aeibi.design.feature.workspace

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProjectTopBar(
    projectName: String,
    onBackClick: () -> Unit,
    onSessionsClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    TopAppBar(
        title = { Text(projectName) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回首页")
            }
        },
        actions = {
            IconButton(onClick = onSessionsClick) {
                Icon(imageVector = Icons.Filled.Menu, contentDescription = "切换会话")
            }
            IconButton(onClick = onPreviewClick) {
                Icon(imageVector = Icons.Filled.OpenInBrowser, contentDescription = "在线预览")
            }
            IconButton(onClick = onMoreClick) {
                Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "项目操作")
            }
        }
    )
}
