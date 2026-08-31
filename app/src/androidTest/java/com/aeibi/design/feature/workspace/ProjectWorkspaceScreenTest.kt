package com.aeibi.design.feature.workspace

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.aeibi.design.R
import com.aeibi.design.data.projects.ProjectRepository
import com.aeibi.design.feature.preview.LocalStaticAssetLoader
import com.aeibi.design.feature.preview.LocalStaticFileServer
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectWorkspaceScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun closingPreviewKeepsBackendAndWebView() {
        val fixture = fixture()
        var workspaceVisible by mutableStateOf(true)
        var previewVisible by mutableStateOf(true)
        var webView: TrackingWebView? = null

        composeTestRule.setContent {
            if (workspaceVisible) {
                val state by fixture.viewModel.previewUiState.collectAsState()
                WorkspacePreviewPane(
                    projectId = PROJECT_ID,
                    visible = previewVisible,
                    fullscreen = false,
                    state = state,
                    viewModel = fixture.viewModel,
                    onBackClick = { previewVisible = false },
                    webViewFactory = { webViewContext, _ ->
                        TrackingWebView(webViewContext).also { webView = it }
                    }
                )
            }
        }

        try {
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                fixture.viewModel.previewUiState.value.status == PreviewStatus.RUNNING && webView != null
            }
            val url = requireNotNull(fixture.viewModel.previewUiState.value.url)
            assertHttpResponse(url, "preview")

            composeTestRule
                .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.back))
                .performClick()
            composeTestRule.waitForIdle()

            assertEquals(PreviewStatus.RUNNING, fixture.viewModel.previewUiState.value.status)
            assertEquals(0, requireNotNull(webView).destroyCalls)
            assertHttpResponse(url, "preview")
        } finally {
            composeTestRule.runOnUiThread {
                workspaceVisible = false
                fixture.store.clear()
            }
        }
    }

    @Test
    fun leavingWorkspaceStopsBackendAndDestroysWebView() {
        val fixture = fixture()
        var workspaceVisible by mutableStateOf(true)
        var webView: TrackingWebView? = null

        composeTestRule.setContent {
            if (workspaceVisible) {
                val state by fixture.viewModel.previewUiState.collectAsState()
                WorkspacePreviewPane(
                    projectId = PROJECT_ID,
                    visible = true,
                    fullscreen = false,
                    state = state,
                    viewModel = fixture.viewModel,
                    webViewFactory = { webViewContext, _ ->
                        TrackingWebView(webViewContext).also { webView = it }
                    }
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            fixture.viewModel.previewUiState.value.status == PreviewStatus.RUNNING && webView != null
        }
        val url = requireNotNull(fixture.viewModel.previewUiState.value.url)
        assertHttpResponse(url, "preview")

        composeTestRule.runOnUiThread {
            workspaceVisible = false
            fixture.store.clear()
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            webView?.destroyCalls == 1
        }
        assertPortClosed(url)
    }

    private fun fixture(): Fixture {
        val projectsRoot = temporaryFolder.newFolder()
        val workspace = File(File(projectsRoot, PROJECT_ID), "workspace").apply { mkdirs() }
        File(workspace, "index.html").writeText("preview")
        val repository = ProjectRepository(
            projectsRoot,
            context.contentResolver,
            context.assets,
            Dispatchers.IO
        )
        val instance = ProjectWorkspaceViewModel(
            repository,
            LocalStaticFileServer(),
            LocalStaticAssetLoader(context),
            Dispatchers.IO
        )
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = instance as T
            }
        )[ProjectWorkspaceViewModel::class.java]
        return Fixture(store, viewModel)
    }

    private fun assertHttpResponse(url: Uri, expectedBody: String) {
        val connection = URL(url.toString()).openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        try {
            assertEquals(200, connection.responseCode)
            assertEquals(expectedBody, connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun assertPortClosed(url: Uri) {
        val connected = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", url.port), 1_000)
            }
        }.isSuccess
        assertFalse("Preview backend is still accepting connections", connected)
    }

    private data class Fixture(val store: ViewModelStore, val viewModel: ProjectWorkspaceViewModel)

    private class TrackingWebView(context: Context) : WebView(context) {
        @Volatile
        var destroyCalls = 0
            private set

        override fun destroy() {
            destroyCalls += 1
            super.destroy()
        }
    }

    private companion object {
        const val PROJECT_ID = "project"
    }
}
