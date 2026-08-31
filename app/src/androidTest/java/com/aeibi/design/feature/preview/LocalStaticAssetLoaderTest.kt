package com.aeibi.design.feature.preview

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 验证 [LocalStaticAssetLoader] 的生命周期与请求拦截逻辑（不依赖真实 UI）。 */
@RunWith(AndroidJUnit4::class)
class LocalStaticAssetLoaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var rootDir: File
    private lateinit var loader: LocalStaticAssetLoader

    @Before
    fun setUp() {
        rootDir = File(context.filesDir, "loader-test").apply { mkdirs() }
        loader = LocalStaticAssetLoader(context)
    }

    @After
    fun tearDown() {
        loader.stop()
        rootDir.deleteRecursively()
    }

    @Test
    fun start_returnsDefaultEntryUrl() {
        val uri = loader.start(rootDir.toPath())
        assertEquals("https://appassets.androidplatform.net/index.html", uri.toString())
    }

    @Test
    fun start_returnsConfiguredEntryUrl() {
        val uri = loader.start(rootDir.toPath(), "pages/home.html")
        assertEquals("https://appassets.androidplatform.net/pages/home.html", uri.toString())
    }

    @Test
    fun start_twice_throwsIllegalState() {
        loader.start(rootDir.toPath())
        try {
            loader.start(rootDir.toPath())
            throw AssertionError("重复 start 应抛 IllegalStateException")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("already running"))
        }
    }

    @Test
    fun intercept_beforeStart_returnsNull() {
        assertNull(loader.shouldInterceptRequest(android.net.Uri.parse("https://appassets.androidplatform.net/")))
    }

    @Test
    fun intercept_servesFileFromRootDirectory() {
        val content = "<html>static</html>"
        File(rootDir, "index.html").writeText(content)
        loader.start(rootDir.toPath())

        val response = loader.shouldInterceptRequest(
            android.net.Uri.parse("https://appassets.androidplatform.net/index.html")
        )

        assertNotNull("index.html 应被拦截返回", response)
        val body = response!!.data.bufferedReader().readText()
        assertEquals(content, body)
        assertTrue(response.mimeType.contains("text/html"))
    }

    @Test
    fun intercept_afterStop_returnsNull() {
        File(rootDir, "index.html").writeText("<html/>")
        loader.start(rootDir.toPath())
        assertNotNull(
            loader.shouldInterceptRequest(android.net.Uri.parse("https://appassets.androidplatform.net/index.html"))
        )

        loader.stop()
        assertNull(
            loader.shouldInterceptRequest(android.net.Uri.parse("https://appassets.androidplatform.net/index.html"))
        )
    }

    @Test
    fun intercept_missingFile_returnsEmptyResponse() {
        loader.start(rootDir.toPath())
        val response = loader.shouldInterceptRequest(
            android.net.Uri.parse("https://appassets.androidplatform.net/missing.html")
        )
        // InternalStoragePathHandler 对缺失文件返回 status=0 的空响应（WebView 视作加载失败）
        assertNotNull(response)
        assertEquals(0, response!!.statusCode)
    }
}
