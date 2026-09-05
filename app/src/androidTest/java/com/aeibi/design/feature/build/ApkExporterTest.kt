package com.aeibi.design.feature.build

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.android.apksig.ApkVerifier
import com.reandroid.apk.ApkModule
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkExporterTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun export_existingProjectIcon_writesLauncherAndRoundIcon() {
        val projectDirectory = temporaryFolder.newFolder("projects", "project-id")
        val workspace = File(projectDirectory, "workspace").apply { mkdir() }
        val index = File(workspace, "index.html").apply { writeText("<h1>Exported project</h1>") }
        val script = File(workspace, "scripts/app.js").apply {
            parentFile?.mkdirs()
            writeText("console.log('exported')")
        }
        val config = File(workspace, "vibe.config.json").apply {
            writeText("""{"build":{"mode":"asset-loader","root":".","entry":"index.html"}}""")
        }
        val projectIcon = File(projectDirectory, "icon-123e4567-e89b-12d3-a456-426614174000.png")
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
            projectIcon.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val output = File(temporaryFolder.root, "exported.apk")

        ApkExporter(context).export(
            workspace = workspace,
            packageName = "com.example.project",
            appName = "Project",
            versionCode = 1,
            versionName = "1.0",
            output = output,
            projectIcon = projectIcon
        )

        assertTrue(output.isFile)
        val signature = ApkVerifier.Builder(output)
            .setMinCheckedPlatformVersion(26)
            .build()
            .verify()
        assertTrue(signature.isVerified)
        assertTrue(signature.isVerifiedUsingV2Scheme)
        assertTrue(signature.isVerifiedUsingV3Scheme)

        ApkModule.loadApkFile(output).use { apk ->
            assertEquals("com.example.project", apk.androidManifest.packageName)
            assertEquals("Project", apk.androidManifest.applicationLabelString)
            assertEquals(1, apk.androidManifest.versionCode)
            assertEquals("1.0", apk.androidManifest.versionName)

            val iconResourceId = apk.androidManifest.iconResourceId
            assertNotEquals(0, iconResourceId)
            assertEquals(iconResourceId, apk.androidManifest.roundIconResourceId)

            val iconEntry = apk.tableBlock.getResource(iconResourceId)
            assertEquals("drawable", iconEntry.type)
            assertEquals("project_icon", iconEntry.name)
            assertEquals("res/drawable/project_icon.png", iconEntry.get().resValue.valueAsString)

            val iconSource = requireNotNull(apk.getInputSource("res/drawable/project_icon.png"))
            assertArrayEquals(projectIcon.readBytes(), iconSource.openStream().use { it.readBytes() })

            assertAssetEquals(apk, "assets/frontend_app/index.html", index)
            assertAssetEquals(apk, "assets/frontend_app/scripts/app.js", script)
            assertAssetEquals(apk, "assets/frontend_app/vibe.config.json", config)
            assertAssetEquals(apk, "assets/vibe.config.json", config)
        }
    }

    @Test
    fun export_missingProjectIcon_keepsTemplateIconConfiguration() {
        val workspace = temporaryFolder.newFolder("workspace")
        val output = File(temporaryFolder.root, "exported.apk")

        ApkExporter(context).export(
            workspace = workspace,
            packageName = "com.example.project",
            appName = "Project",
            versionCode = 1,
            versionName = "1.0",
            output = output,
            projectIcon = File(temporaryFolder.root, "icon-missing.png")
        )

        ApkModule.loadApkFile(output).use { apk ->
            assertEquals(0, apk.androidManifest.iconResourceId)
            assertEquals(0, apk.androidManifest.roundIconResourceId)
            assertEquals(null, apk.getInputSource("res/drawable/project_icon.png"))
        }
    }

    private fun assertAssetEquals(apk: ApkModule, path: String, expected: File) {
        val source = requireNotNull(apk.getInputSource(path))
        assertArrayEquals(expected.readBytes(), source.openStream().use { it.readBytes() })
    }
}
