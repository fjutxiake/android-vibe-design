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
import com.aeibi.design.feature.chat.ChatViewModel
import com.aeibi.design.feature.preview.ConsoleScreen
import com.aeibi.design.feature.preview.ProjectPreviewScreen
import com.aeibi.design.feature.projects.ProjectsViewModel
import com.aeibi.design.feature.sessions.SessionDrawer
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
    // 与 ChatScreen 内部 hiltViewModel() 同属本导航 entry 的 ViewModelStore——同一实例。
    val chatViewModel: ChatViewModel = hiltViewModel()
    // 回合完成（agent 可能改了文件）→ 预览内容版本 +1，可见时自动重新加载。
    LaunchedEffect(Unit) {
        chatViewModel.turnCompleted.collect { workspaceViewModel.onAgentTurnCompleted() }
    }
    // agent 回合内 reload_preview 工具请求 → 执行刷新（RUNNING 且可见时）。
    LaunchedEffect(Unit) {
        chatViewModel.previewReloadRequested.collect { workspaceViewModel.onPreviewReloadRequested() }
    }
    // lambda 中无法调用 stringResource,先在组合作用域取好文本再闭包引用。
    val deleteFailedText = stringResource(R.string.projects_delete_failed)

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
            onConsoleClick = { pane = WorkspacePane.CONSOLE },
            onReportErrorToAi = { _ ->
                // 报告文本（折叠摘要 + 时间轴表格）在 VM 组装；发送前取文本，dismiss 会清明细。
                val reportText = workspaceViewModel.buildErrorReportText()
                workspaceViewModel.dismissPageError()
                if (reportText != null) {
                    chatViewModel.sendText(reportText, onSessionCreated = { selectedSessionId = it })
                }
                pane = WorkspacePane.CHAT
            }
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
            onDeleteClick = { showDeleteConfirm = true }
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
    onReportErrorToAi: ((PreviewPageError) -> Unit)? = null,
    webViewFactory: (Context, ProjectWorkspaceViewModel) -> WebView = ::createPreviewWebView
) {
    val context = LocalContext.current
    var previewOpened by remember(projectId) { mutableStateOf(false) }
    var webView by remember(projectId) { mutableStateOf<WebView?>(null) }
    var loadedUrl by remember(projectId) { mutableStateOf<String?>(null) }
    var loadedContentVersion by remember(projectId) { mutableStateOf(-1) }
    var handledReloadTick by remember(projectId) { mutableStateOf(0) }

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

    // 内容版本驱动加载：首次/换地址 → loadUrl；回合完成（版本 +1）或 agent 主动请求
    // （reloadRequestTick +1）→ 自动 reload。
    LaunchedEffect(visible, state.status, state.url, state.contentVersion, state.reloadRequestTick, webView) {
        if (!visible || state.status != PreviewStatus.RUNNING) return@LaunchedEffect
        val urlString = state.url?.toString() ?: return@LaunchedEffect
        if (urlString != loadedUrl) {
            loadedUrl = urlString
            loadedContentVersion = state.contentVersion
            handledReloadTick = state.reloadRequestTick
            viewModel.onNavigationStarted()
            webView?.loadUrl(urlString)
        } else if (state.contentVersion > loadedContentVersion) {
            loadedContentVersion = state.contentVersion
            handledReloadTick = state.reloadRequestTick
            viewModel.onNavigationStarted()
            webView?.reload()
        } else if (state.reloadRequestTick > handledReloadTick) {
            handledReloadTick = state.reloadRequestTick
            viewModel.onNavigationStarted()
            webView?.reload()
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
            // 只清展示面板——store 里的旧错误是 agent 下一回合的诊断依据（clear_runtime_logs/控制台显式清空才清 store）。
            viewModel.clearConsolePanel()
            viewModel.onNavigationStarted()
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

    // 主 frame 加载失败：纯确认弹窗（详情在控制台/AI 日志里）——用户裁决后发给 AI（Figma Make 节奏，不自动注入）。
    state.pageError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPageError,
            title = { Text(stringResource(R.string.preview_error_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.preview_error_summary,
                        error.count,
                        android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(error.atMillis))
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                // dismiss 由 onReportErrorToAi 内部处理（发送前需先取报告文本，dismiss 会清明细）；未接线时兜底 dismiss。
                TextButton(onClick = {
                    if (onReportErrorToAi != null) {
                        onReportErrorToAi?.invoke(error)
                    } else {
                        viewModel.dismissPageError()
                    }
                }) {
                    Text(stringResource(R.string.preview_error_send_to_ai))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPageError) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                if (request.isForMainFrame) {
                    viewModel.recordPageError(
                        error.errorCode,
                        error.description?.toString().orEmpty(),
                        request.url.toString()
                    )
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame) {
                    viewModel.recordPageError(
                        errorResponse.statusCode,
                        "HTTP ${errorResponse.statusCode}",
                        request.url.toString()
                    )
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                viewModel.onPageFinished()
            }
        }
        webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                viewModel.recordConsoleMessage(consoleMessage)
                return true
            }
        }
    }
