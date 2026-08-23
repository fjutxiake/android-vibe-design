package com.aeibi.design.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.aeibi.design.theme.VibeDesignTheme
import com.aeibi.design.theme.spacing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProjectsScreen(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {},
    onProjectClick: (String) -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    var showNewProjectSheet by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Vibe Design") },
                actions = {
                    IconButton(
                        onClick = { showNewProjectSheet = true },
                        modifier = Modifier.testTag("new_project_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "新建项目")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(imageVector = Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item {
                ProjectListItem(
                    name = "日常发芽",
                    description = "不焦虑的日常习惯记录",
                    updatedAt = "刚刚修改",
                    onClick = { onProjectClick("daily-growth") }
                )
            }
            item {
                ProjectListItem(
                    name = "周末去哪",
                    description = "根据心情生成短途路线",
                    updatedAt = "昨天修改",
                    onClick = { onProjectClick("weekend-trip") }
                )
            }
            item {
                ProjectListItem(
                    name = "专注计时器",
                    description = "把大任务切成可完成的小段",
                    updatedAt = "8月6日修改",
                    onClick = { onProjectClick("focus-timer") }
                )
            }
            item {
                ProjectListItem(
                    name = "专注计时器",
                    description = "把大任务切成可完成的小段",
                    updatedAt = "8月6日修改",
                    onClick = { onProjectClick("focus-timer-2") }
                )
            }
            item {
                ProjectListItem(
                    name = "专注计时器",
                    description = "把大任务切成可完成的小段",
                    updatedAt = "8月6日修改",
                    onClick = { onProjectClick("focus-timer-3") }
                )
            }
            item {
                ProjectListItem(
                    name = "专注计时器",
                    description = "把大任务切成可完成的小段",
                    updatedAt = "8月6日修改",
                    onClick = { onProjectClick("focus-timer-4") }
                )
            }
            item {
                ProjectListItem(
                    name = "专注计时器",
                    description = "把大任务切成可完成的小段",
                    updatedAt = "8月6日修改",
                    onClick = { onProjectClick("focus-timer-5") }
                )
            }
            item {
                ProjectListItem(
                    name = "专注计时器",
                    description = "把大任务切成可完成的小段",
                    updatedAt = "8月6日修改",
                    onClick = { onProjectClick("focus-timer-6") }
                )
            }
            item {
                ProjectListItem(
                    name = "专注计时器",
                    description = "把大任务切成可完成的小段",
                    updatedAt = "8月6日修改",
                    onClick = { onProjectClick("focus-timer-7") }
                )
            }
        }
    }

    if (showNewProjectSheet) {
        NewProjectBottomSheet(onDismiss = { showNewProjectSheet = false })
    }
}

@Preview(showBackground = true)
@Composable
fun ProjectsScreenPreview() {
    VibeDesignTheme(dynamicColor = false) { ProjectsScreen() }
}

@Preview(showBackground = true, widthDp = 340, heightDp = 700)
@Composable
fun ProjectsScreenPortraitPreview() {
    VibeDesignTheme(dynamicColor = false) { ProjectsScreen() }
}
