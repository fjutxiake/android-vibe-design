package com.aeibi.design.feature.workspace

import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.ConsoleMessage.MessageLevel
import androidx.test.core.app.ApplicationProvider
import com.aeibi.design.data.projects.ProjectRepository
import com.aeibi.design.data.runtimelogs.RuntimeLogStore
import com.aeibi.design.feature.preview.LocalStaticAssetLoader
import com.aeibi.design.feature.preview.LocalStaticFileServer
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectWorkspaceViewModelTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun missingConfigStartsDefaultHttpServer() {
        val fixture = fixture()
        File(fixture.workspace, "index.html").writeText("preview")

        fixture.viewModel.startPreview(PROJECT_ID)

        val state = awaitStatus(fixture.viewModel, PreviewStatus.RUNNING)
        assertTrue(state.url.toString().startsWith("http://localhost:"))
        fixture.stop()
    }

    @Test
    fun missingPreviewAndPartialPreviewUseDefaults() {
        val missingPreview = fixture("{}")
        File(missingPreview.workspace, "index.html").writeText("preview")
        missingPreview.viewModel.startPreview(PROJECT_ID)
        assertTrue(
            awaitStatus(missingPreview.viewModel, PreviewStatus.RUNNING)
                .url.toString().startsWith("http://localhost:")
        )
        missingPreview.stop()

        val partialPreview = fixture("""{"preview":{"mode":"asset-loader"}}""")
        File(partialPreview.workspace, "index.html").writeText("preview")
        partialPreview.viewModel.startPreview(PROJECT_ID)
        assertEquals(
            "https://appassets.androidplatform.net/index.html",
            awaitStatus(partialPreview.viewModel, PreviewStatus.RUNNING).url.toString()
        )
        partialPreview.stop()
    }

    @Test
    fun assetLoaderUsesConfiguredEntry() {
        val fixture = fixture("""{"preview":{"mode":"asset-loader","entry":"pages/home.html"}}""")

        fixture.viewModel.startPreview(PROJECT_ID)

        assertEquals(
            "https://appassets.androidplatform.net/pages/home.html",
            awaitStatus(fixture.viewModel, PreviewStatus.RUNNING).url.toString()
        )
        fixture.stop()
    }

    @Test
    fun httpServerIgnoresEntry() {
        val fixture = fixture("""{"preview":{"entry":"missing.html"}}""")

        fixture.viewModel.startPreview(PROJECT_ID)

        assertTrue(
            awaitStatus(fixture.viewModel, PreviewStatus.RUNNING)
                .url.toString().startsWith("http://localhost:")
        )
        fixture.stop()
    }

    @Test
    fun malformedConfigFailsAndCanRestart() {
        val fixture = fixture("{malformed")

        fixture.viewModel.startPreview(PROJECT_ID)
        val failed = awaitStatus(fixture.viewModel, PreviewStatus.FAILED)
        assertNull(failed.url)

        File(fixture.workspace, CONFIG_FILE).writeText("""{"preview":{"mode":"asset-loader"}}""")
        fixture.viewModel.startPreview(PROJECT_ID)
        assertEquals(
            "https://appassets.androidplatform.net/index.html",
            awaitStatus(fixture.viewModel, PreviewStatus.RUNNING).url.toString()
        )
        fixture.stop()
    }

    @Test
    fun stopIsRepeatableAndAllowsRestart() {
        val fixture = fixture("""{"preview":{"mode":"asset-loader"}}""")
        fixture.viewModel.startPreview(PROJECT_ID)
        awaitStatus(fixture.viewModel, PreviewStatus.RUNNING)

        fixture.viewModel.stopPreview()
        awaitStatus(fixture.viewModel, PreviewStatus.STOPPED)
        fixture.viewModel.stopPreview()
        fixture.viewModel.startPreview(PROJECT_ID)

        awaitStatus(fixture.viewModel, PreviewStatus.RUNNING)
        fixture.stop()
    }

    @Test
    fun consoleMessages_accumulateAllLevelsWhileRunningAndClear() {
        val fixture = fixture()
        File(fixture.workspace, "index.html").writeText("preview")
        fixture.viewModel.startPreview(PROJECT_ID)
        awaitStatus(fixture.viewModel, PreviewStatus.RUNNING)

        fixture.viewModel.recordConsoleMessage(consoleMessage("JS: hello", MessageLevel.LOG))
        fixture.viewModel.recordConsoleMessage(
            consoleMessage("JS: ReferenceError: x is not defined", MessageLevel.ERROR)
        )
        fixture.viewModel.recordConsoleMessage(consoleMessage("JS: deprecation warning", MessageLevel.WARNING))

        val state = fixture.viewModel.previewUiState.value
        assertEquals(3, state.consoleMessages.size)
        // 全级别保留
        assertTrue(state.consoleMessages.any { it.message().contains("hello") })
        assertTrue(state.consoleMessages.any { it.message().contains("ReferenceError") })
        assertTrue(state.consoleMessages.any { it.message().contains("deprecation") })

        fixture.viewModel.clearConsoleMessages()
        assertTrue(fixture.viewModel.previewUiState.value.consoleMessages.isEmpty())
        fixture.stop()
    }

    @Test
    fun consoleMessages_ignoredWhenNotRunning() {
        val fixture = fixture()
        File(fixture.workspace, "index.html").writeText("preview")

        fixture.viewModel.recordConsoleMessage(consoleMessage("JS: should be ignored", MessageLevel.ERROR))

        assertTrue(fixture.viewModel.previewUiState.value.consoleMessages.isEmpty())
        fixture.stop()
    }

    @Test
    fun pageError_countsErrorsAndClearsAfterSuccessfulNavigation() {
        val fixture = fixture()
        File(fixture.workspace, "index.html").writeText("preview")

        fixture.viewModel.recordPageError(404, "HTTP 404", "http://localhost/index.html")
        assertNull("Error before preview start is ignored", fixture.viewModel.previewUiState.value.pageError)
        assertEquals("Nothing recorded before running", 0, fixture.storeSnapshot())

        fixture.viewModel.startPreview(PROJECT_ID)
        awaitStatus(fixture.viewModel, PreviewStatus.RUNNING)
        fixture.viewModel.recordPageError(-6, "ERR_FILE_NOT_FOUND", "http://localhost/missing.html")
        val firstError = fixture.viewModel.previewUiState.value.pageError
        assertEquals("Dialog carries an error count", 1, requireNotNull(firstError).count)
        assertEquals("Error carries a file anchor for the AI", "missing.html", firstError.file)
        assertEquals(
            "Detail lands in console panel for the user",
            1,
            fixture.viewModel.previewUiState.value.consoleMessages.size
        )
        assertEquals("Detail lands in store for the agent tool", 1, fixture.storeSnapshot())

        fixture.viewModel.recordPageError(500, "HTTP 500", "http://localhost/other.html")
        assertEquals(
            "Dialog stays open with an accumulating count",
            2,
            fixture.viewModel.previewUiState.value.pageError?.count
        )
        assertEquals(2, fixture.storeSnapshot())

        // 错误导航完成（错误页自身也会 finished）不清错误
        fixture.viewModel.onPageFinished()
        assertNotNull("Failed navigation's finished keeps the error", fixture.viewModel.previewUiState.value.pageError)

        // 新一轮无错误导航完成才算页面恢复
        fixture.viewModel.onNavigationStarted()
        fixture.viewModel.onPageFinished()
        assertNull("Successful navigation clears the error", fixture.viewModel.previewUiState.value.pageError)

        // 恢复后再次失败 → 新计数从头累计
        fixture.viewModel.recordPageError(500, "HTTP 500", "http://localhost/other.html")
        assertEquals(1, fixture.viewModel.previewUiState.value.pageError?.count)
        assertEquals(3, fixture.storeSnapshot())
        fixture.stop()
    }

    @Test
    fun errorReportText_buildsCollapsibleTimelineTable() {
        val fixture = fixture()
        File(fixture.workspace, "index.html").writeText("preview")

        assertNull("No report without a failure", fixture.viewModel.buildErrorReportText())

        fixture.viewModel.startPreview(PROJECT_ID)
        awaitStatus(fixture.viewModel, PreviewStatus.RUNNING)
        fixture.viewModel.recordPageError(404, "HTTP 404", "http://localhost/missing.html")
        fixture.viewModel.recordPageError(-6, "ERR_FILE_NOT_FOUND", "http://localhost/missing.html")

        val report = fixture.viewModel.buildErrorReportText()
        assertTrue(
            requireNotNull(report).startsWith("[error-report] Preview load failure — 2 error(s) on missing.html at ")
        )
        assertTrue(report.contains("| # | Time | Error | File |"))
        assertTrue(report.contains("| 1 |"))
        assertTrue(report.contains("| HTTP 404 |"))
        assertTrue(report.contains("| 2 |"))
        assertTrue(report.contains("| ERR_FILE_NOT_FOUND |"))
        assertTrue("Rows carry workspace file paths, not raw URLs", report.contains("| missing.html |"))

        // dismiss 清明细——再发就是 null（本次失败周期结束）
        fixture.viewModel.dismissPageError()
        assertNull(fixture.viewModel.buildErrorReportText())
        fixture.stop()
    }

    @Test
    fun agentTurnCompletedBumpsContentVersion() {
        val fixture = fixture()
        assertEquals(0, fixture.viewModel.previewUiState.value.contentVersion)

        fixture.viewModel.onAgentTurnCompleted()
        fixture.viewModel.onAgentTurnCompleted()

        assertEquals(2, fixture.viewModel.previewUiState.value.contentVersion)
        fixture.stop()
    }

    @Test
    fun previewReloadRequestsAreCountedIndependently() {
        val fixture = fixture()
        assertEquals(0, fixture.viewModel.previewUiState.value.reloadRequestTick)

        fixture.viewModel.onPreviewReloadRequested()
        fixture.viewModel.onPreviewReloadRequested()
        fixture.viewModel.onAgentTurnCompleted()

        assertEquals(
            "Reload requests tick independently of content version",
            2,
            fixture.viewModel.previewUiState.value.reloadRequestTick
        )
        assertEquals(1, fixture.viewModel.previewUiState.value.contentVersion)
        fixture.stop()
    }

    @Test
    fun refreshClearsPanelButKeepsStore_explicitClearClearsBoth() {
        val fixture = fixture()
        File(fixture.workspace, "index.html").writeText("preview")
        fixture.viewModel.startPreview(PROJECT_ID)
        awaitStatus(fixture.viewModel, PreviewStatus.RUNNING)

        fixture.viewModel.recordConsoleMessage(consoleMessage("diagnostic error", MessageLevel.ERROR))
        assertEquals(1, fixture.viewModel.previewUiState.value.consoleMessages.size)
        assertEquals(1, fixture.storeSnapshot())

        // 手动刷新（重新加载）只清展示面板——store 的错误留给 agent 下一回合诊断
        fixture.viewModel.clearConsolePanel()
        assertTrue(fixture.viewModel.previewUiState.value.consoleMessages.isEmpty())
        assertEquals("Store keeps diagnostics across refresh", 1, fixture.storeSnapshot())

        // 显式清空 = 面板 + store 双清
        fixture.viewModel.clearConsoleMessages()
        assertEquals(0, fixture.storeSnapshot())
        fixture.stop()
    }

    @Test
    fun consoleMessages_areNotTruncated() {
        val fixture = fixture()
        File(fixture.workspace, "index.html").writeText("preview")
        fixture.viewModel.startPreview(PROJECT_ID)
        awaitStatus(fixture.viewModel, PreviewStatus.RUNNING)

        fixture.viewModel.recordConsoleMessage(consoleMessage("important error", MessageLevel.ERROR))
        repeat(60) { fixture.viewModel.recordConsoleMessage(consoleMessage("noise-$it", MessageLevel.LOG)) }

        val state = fixture.viewModel.previewUiState.value
        assertEquals(61, state.consoleMessages.size)
        assertEquals("important error", state.consoleMessages.first().message())
        fixture.stop()
    }

    private fun consoleMessage(text: String, level: MessageLevel): ConsoleMessage =
        ConsoleMessage(text, "test.js", 1, level)

    private fun fixture(config: String? = null): Fixture {
        val projectsRoot = temporaryFolder.newFolder()
        val workspace = File(File(projectsRoot, PROJECT_ID), "workspace").apply { mkdirs() }
        config?.let { File(workspace, CONFIG_FILE).writeText(it) }
        val repository = ProjectRepository(
            projectsRoot,
            context.contentResolver,
            context.assets,
            Dispatchers.IO
        )
        val store = RuntimeLogStore()
        return Fixture(
            workspace,
            ProjectWorkspaceViewModel(
                repository,
                LocalStaticFileServer(),
                LocalStaticAssetLoader(context),
                store,
                Dispatchers.IO
            ),
            store
        )
    }

    private fun Fixture.storeSnapshot(): Int = store.snapshot(PROJECT_ID).size

    private fun awaitStatus(viewModel: ProjectWorkspaceViewModel, status: PreviewStatus): PreviewUiState {
        val timeout = System.currentTimeMillis() + 5_000
        while (viewModel.previewUiState.value.status != status && System.currentTimeMillis() < timeout) {
            Thread.sleep(10)
        }
        return viewModel.previewUiState.value.also { assertEquals(status, it.status) }
    }

    private data class Fixture(
        val workspace: File,
        val viewModel: ProjectWorkspaceViewModel,
        val store: RuntimeLogStore
    ) {
        fun stop() {
            viewModel.stopPreview()
            val timeout = System.currentTimeMillis() + 5_000
            while (
                viewModel.previewUiState.value.status != PreviewStatus.STOPPED &&
                System.currentTimeMillis() < timeout
            ) {
                Thread.sleep(10)
            }
            assertEquals(PreviewStatus.STOPPED, viewModel.previewUiState.value.status)
        }
    }

    private companion object {
        const val PROJECT_ID = "project"
        const val CONFIG_FILE = "vibe.config.json"
    }
}
