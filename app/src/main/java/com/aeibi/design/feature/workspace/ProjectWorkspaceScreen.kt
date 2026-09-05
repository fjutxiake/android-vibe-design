package com.aeibi.design.feature.workspace

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aeibi.design.R
import com.aeibi.design.feature.chat.ChatScreen
import com.aeibi.design.feature.preview.ConsoleScreen
import com.aeibi.design.feature.preview.ProjectPreviewScreen
import com.aeibi.design.feature.projects.ProjectsViewModel
import com.aeibi.design.feature.sessions.SessionDrawer
import java.io.File
import kotlinx.coroutines.launch

private enum class WorkspacePane {
    CHAT,
    PREVIEW,
    CONSOLE
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProjectWorkspaceScreen(
    projectId: String,
    modifier: Modifier = Modifier,
    onProjectPickerClick: () -> Unit = {},
    onBuildClick: () -> Unit = {},
    onVersionsClick: () -> Unit = {},
    onProjectSettingsClick: () -> Unit = {},
    onAppSettingsClick: () -> Unit = {},
    viewModel: ProjectsViewModel = hiltViewModel(),
    workspaceViewModel: ProjectWorkspaceViewModel = hiltViewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedSessionId by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var showProjectActions by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var pane by rememberSaveable(projectId) { mutableStateOf(WorkspacePane.CHAT) }
    var fullscreen by rememberSaveable(projectId) { mutableStateOf(false) }
    val project by viewModel.observeProject(projectId).collectAsState(initial = null)
    val previewState by workspaceViewModel.previewUiState.collectAsState()
    // lambda 中无法调用 stringResource,先在组合作用域取好文本再闭包引用。
    val deleteFailedText = stringResource(R.string.projects_delete_failed)
    val exportFailedText = stringResource(R.string.workspace_export_failed)
    val exportSavedText = stringResource(R.string.workspace_export_saved)
    val context = LocalContext.current
    var exporting by rememberSaveable { mutableStateOf(false) }
    // 导出：用户选保存位置（CreateDocument）→ 导出 zip → 写入所选 uri
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null && !exporting) {
            exporting = true
            val outputFile = File(context.cacheDir, "export-${System.currentTimeMillis()}.zip")
            viewModel.exportWorkspace(projectId, outputFile) { result ->
                result
                    .onSuccess { file ->
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                file.inputStream().use { it.copyTo(out) }
                            } ?: error("Cannot open export destination")
                        }.onFailure {
                            scope.launch { snackbarHostState.showSnackbar(exportFailedText) }
                        }
                        file.delete()
                        scope.launch { snackbarHostState.showSnackbar(exportSavedText) }
                    }
                    .onFailure {
                        scope.launch { snackbarHostState.showSnackbar(exportFailedText) }
                    }
                exporting = false
            }
        }
    }

    fun closePreview() {
        if (fullscreen) {
            fullscreen = false
        } else if (pane == WorkspacePane.CONSOLE) {
            pane = WorkspacePane.PREVIEW
        } else {
            pane = WorkspacePane.CHAT
        }
    }

    BackHandler(enabled = pane != WorkspacePane.CHAT, onBack = ::closePreview)

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = pane == WorkspacePane.CHAT,
        drawerContent = {
            SessionDrawer(
                projectId = projectId,
                selectedSessionId = selectedSessionId,
                onSessionSelected = { sessionId ->
                    selectedSessionId = sessionId
                    scope.launch { drawerState.close() }
                },
                onCurrentSessionDeleted = {
                    selectedSessionId = null
                    scope.launch { drawerState.close() }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = if (pane == WorkspacePane.CHAT) Modifier.fillMaxSize() else Modifier.size(0.dp),
            topBar = {
                ProjectTopBar(
                    projectName = project?.name ?: stringResource(R.string.workspace_unnamed_project),
                    onBackClick = onProjectPickerClick,
                    onSessionsClick = { scope.launch { drawerState.open() } },
                    onPreviewClick = { pane = WorkspacePane.PREVIEW },
                    onMoreClick = { showProjectActions = true }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            ChatScreen(
                projectId = projectId,
                sessionId = selectedSessionId,
                onSessionCreated = { selectedSessionId = it },
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
        }

        WorkspacePreviewPane(
            projectId = projectId,
            visible = pane == WorkspacePane.PREVIEW,
            fullscreen = fullscreen,
            state = previewState,
            viewModel = workspaceViewModel,
            onBackClick = ::closePreview,
            onFullscreenClick = { fullscreen = true },
            onConsoleClick = { pane = WorkspacePane.CONSOLE }
        )

        if (pane == WorkspacePane.CONSOLE) {
            ConsoleScreen(
                messages = previewState.consoleMessages,
                onBackClick = ::closePreview,
                onClearClick = workspaceViewModel::clearConsoleMessages
            )
        }
    }

    if (showProjectActions) {
        ProjectActionsSheet(
            onDismiss = { showProjectActions = false },
            onBuildClick = onBuildClick,
            onVersionsClick = onVersionsClick,
            onProjectSettingsClick = onProjectSettingsClick,
            onAppSettingsClick = onAppSettingsClick,
            onDeleteClick = { showDeleteConfirm = true },
            onExportClick = {
                if (!exporting) {
                    exportLauncher.launch("${project?.name?.takeIf(String::isNotBlank) ?: "project"}.zip")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_project_title)) },
            text = { Text(stringResource(R.string.delete_project_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        // 删除真正成功才退回项目列表;失败就留在当前项目并提示,不假装已经删掉。
                        viewModel.deleteProject(projectId) { result ->
                            result
                                .onSuccess { onProjectPickerClick() }
                                .onFailure {
                                    scope.launch { snackbarHostState.showSnackbar(deleteFailedText) }
                                }
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
internal fun WorkspacePreviewPane(
    projectId: String,
    visible: Boolean,
    fullscreen: Boolean,
    state: PreviewUiState,
    viewModel: ProjectWorkspaceViewModel,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onFullscreenClick: () -> Unit = {},
    onConsoleClick: () -> Unit = {},
    webViewFactory: (Context, ProjectWorkspaceViewModel) -> WebView = ::createPreviewWebView
) {
    val context = LocalContext.current
    var previewOpened by remember(projectId) { mutableStateOf(false) }
    var webView by remember(projectId) { mutableStateOf<WebView?>(null) }

    LaunchedEffect(visible, projectId) {
        if (visible) {
            if (!previewOpened) {
                previewOpened = true
                viewModel.startPreview(projectId)
            }
            if (webView == null) {
                webView = webViewFactory(context, viewModel)
            }
        }
    }

    LaunchedEffect(visible, webView) {
        webView?.let {
            if (visible) it.onResume() else it.onPause()
        }
    }

    LaunchedEffect(state.status, state.url, webView) {
        if (state.status == PreviewStatus.RUNNING) {
            state.url?.let { webView?.loadUrl(it.toString()) }
        }
    }

    webView?.let { previewWebView ->
        DisposableEffect(previewWebView) {
            onDispose {
                (previewWebView.parent as? ViewGroup)?.removeView(previewWebView)
                previewWebView.stopLoading()
                previewWebView.destroy()
            }
        }
    }

    ProjectPreviewScreen(
        state = state,
        modifier = if (visible) modifier.fillMaxSize() else Modifier.size(0.dp),
        fullscreen = fullscreen,
        onBackClick = onBackClick,
        onRefreshClick = {
            viewModel.clearConsoleMessages()
            webView?.reload()
        },
        onToggleBackendClick = {
            if (state.status == PreviewStatus.RUNNING) {
                viewModel.stopPreview()
            } else {
                viewModel.startPreview(projectId)
            }
        },
        onFullscreenClick = onFullscreenClick,
        onConsoleClick = onConsoleClick
    ) { webViewModifier ->
        webView?.let { previewWebView ->
            AndroidView(
                factory = { previewWebView },
                modifier = webViewModifier,
                update = {
                    it.visibility = if (visible) View.VISIBLE else View.INVISIBLE
                }
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createPreviewWebView(context: Context, viewModel: ProjectWorkspaceViewModel): WebView =
    WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? =
                viewModel.shouldInterceptRequest(request.url)
        }
        webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                viewModel.recordConsoleMessage(consoleMessage)
                return true
            }
        }
    }
