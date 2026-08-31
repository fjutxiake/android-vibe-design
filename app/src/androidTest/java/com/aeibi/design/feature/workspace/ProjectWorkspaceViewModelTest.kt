package com.aeibi.design.feature.workspace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aeibi.design.data.projects.ProjectRepository
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
        return Fixture(
            workspace,
            ProjectWorkspaceViewModel(
                repository,
                LocalStaticFileServer(),
                LocalStaticAssetLoader(context),
                Dispatchers.IO
            )
        )
    }

    private fun awaitStatus(viewModel: ProjectWorkspaceViewModel, status: PreviewStatus): PreviewUiState {
        val timeout = System.currentTimeMillis() + 5_000
        while (viewModel.previewUiState.value.status != status && System.currentTimeMillis() < timeout) {
            Thread.sleep(10)
        }
        return viewModel.previewUiState.value.also { assertEquals(status, it.status) }
    }

    private data class Fixture(val workspace: File, val viewModel: ProjectWorkspaceViewModel) {
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
