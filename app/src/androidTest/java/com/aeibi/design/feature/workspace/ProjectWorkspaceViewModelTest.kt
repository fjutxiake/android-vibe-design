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
    fun consoleMessages_accumulateInStoreAllLevelsWhileRunningAndClear() {
        val fixture = fixture()
        File(fixture.workspace, "index.html").writeText("preview")
        fixture.viewModel.startPreview(PROJECT_ID)
        awaitStatus(fixture.viewModel, PreviewStatus.RUNNING)

        fixture.viewModel.recordConsoleMessage(consoleMessage("JS: hello", MessageLevel.LOG))
        fixture.viewModel.recordConsoleMessage(
            consoleMessage("JS: ReferenceError: x is not defined", MessageLevel.ERROR)
        )
        fixture.viewModel.recordConsoleMessage(consoleMessage("JS: deprecation warning", MessageLevel.WARNING))

        val entries = fixture.store.snapshot(PROJECT_ID)
        assertEquals(3, entries.size)
        // 全级别保留（单一日志源：Console 展示与 agent 工具读同一份）
        assertTrue(entries.any { it.message.contains("hello") })
        assertTrue(entries.any { it.message.contains("ReferenceError") })
        assertTrue(entries.any { it.message.contains("deprecation") })

        fixture.viewModel.clearLogs()
        assertTrue(fixture.store.snapshot(PROJECT_ID).isEmpty())
        fixture.stop()
    }

    @Test
    fun consoleMessages_ignoredWhenNotRunning() {
        val fixture = fixture()
        File(fixture.workspace, "index.html").writeText("preview")

        fixture.viewModel.recordConsoleMessage(consoleMessage("JS: should be ignored", MessageLevel.ERROR))

        assertTrue(fixture.store.snapshot(PROJECT_ID).isEmpty())
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

        val entries = fixture.store.snapshot(PROJECT_ID)
        assertEquals(61, entries.size)
        assertEquals("important error", entries.first().message)
        fixture.stop()
    }

    @Test
    fun pageLoadFailureIsRecordedAsLogWithoutDialog() {
        val fixture = fixture()
        File(fixture.workspace, "index.html").writeText("preview")
        fixture.viewModel.startPreview(PROJECT_ID)
        awaitStatus(fixture.viewModel, PreviewStatus.RUNNING)

        fixture.viewModel.recordPageError(404, "HTTP 404", "http://localhost/index.html")
        fixture.viewModel.recordPageError(-6, "ERR_FILE_NOT_FOUND", "http://localhost/missing.html")

        val entries = fixture.store.snapshot(PROJECT_ID)
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.level == "ERROR" })
        assertTrue(entries.any { it.message.contains("HTTP 404") })
        // 无弹窗状态——失败只进日志
        assertNull(fixture.viewModel.previewUiState.value.errorMessage)
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
