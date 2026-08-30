package com.aeibi.design.apk.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 验证 [AndroidApkEngine] 的 decode / build 全链路（ARSCLib 重定位版，真机）。 */
@RunWith(AndroidJUnit4::class)
class AndroidApkEngineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var engine: AndroidApkEngine

    @Before
    fun setUp() {
        engine = AndroidApkEngine(context)
    }

    @Test
    fun decode_manifestsAreDecodedToPlainXml() {
        val template = extractTemplate()
        val decodedDir = File(context.cacheDir, "decode-test").apply {
            deleteRecursively()
            mkdirs()
        }

        engine.decode(template.toPath(), decodedDir.toPath())

        val manifest = decodedDir.resolve("AndroidManifest.xml")
        assertTrue("manifest 应被解码为明文 XML: $manifest", manifest.exists())
        val content = manifest.readText()
        assertTrue("manifest 应含包名: $content", content.contains("com.vibetest.mini"))
    }

    @Test
    fun build_rebuildsApkWithSummary() {
        val template = extractTemplate()
        val decodedDir = File(context.cacheDir, "build-test-decoded").apply {
            deleteRecursively()
            mkdirs()
        }
        val rebuilt = File(context.cacheDir, "build-test-rebuilt.apk")

        engine.decode(template.toPath(), decodedDir.toPath())
        val summary = engine.build(decodedDir.toPath(), rebuilt.toPath())

        assertTrue("重打包产物应存在", rebuilt.exists())
        assertTrue("产物应有体积", summary.apkSizeBytes > 0)
        assertTrue("产物应有条目", summary.entryCount > 0)
        assertFalse("最小模板无 dex", summary.hasDex)
    }

    @Test
    fun build_outputCanBeDecodedAgain() {
        val template = extractTemplate()
        val decodedDir = File(context.cacheDir, "roundtrip-decoded").apply {
            deleteRecursively()
            mkdirs()
        }
        val rebuilt = File(context.cacheDir, "roundtrip.apk")
        val reDecoded = File(context.cacheDir, "roundtrip-redecoded").apply {
            deleteRecursively()
            mkdirs()
        }

        engine.decode(template.toPath(), decodedDir.toPath())
        engine.build(decodedDir.toPath(), rebuilt.toPath())
        engine.decode(rebuilt.toPath(), reDecoded.toPath())

        val manifest = reDecoded.resolve("AndroidManifest.xml")
        assertNotNull("roundtrip 后 manifest 应仍可解码", manifest.takeIf { it.exists() })
        assertTrue(manifest.readText().contains("com.vibetest.mini"))
    }

    @Test
    fun decode_oldTargetSdk_fallsBackToNearestFramework() {
        // framework 只内置 android-36：targetSdk 23 的模板应经 getNearestVersion 近似解析
        val template = extractTemplate("min-template-23.apk", "min-template-23")
        val decodedDir = File(context.cacheDir, "decode23-test").apply {
            deleteRecursively()
            mkdirs()
        }

        engine.decode(template.toPath(), decodedDir.toPath())

        val manifest = decodedDir.resolve("AndroidManifest.xml")
        assertTrue("老 targetSdk 模板应可解码: $manifest", manifest.exists())
        val content = manifest.readText()
        assertTrue("包名应正确: $content", content.contains("com.vibetest.mini23"))
    }

    /** 从 target APK 的 assets 提取最小模板（与 frameworks 同类为构建期资源）。 */
    private fun extractTemplate(assetName: String = MIN_TEMPLATE_ASSET, cacheName: String = assetName): File {
        val target = File(context.cacheDir, cacheName)
        context.assets.open(assetName).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        assertTrue("模板应已提取", target.length() > 0)
        return target
    }

    private companion object {
        const val MIN_TEMPLATE_ASSET = "min-template.apk"
    }
}
