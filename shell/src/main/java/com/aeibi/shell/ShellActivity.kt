package com.aeibi.shell

import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class ShellActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var loadingIndicator: ProgressBar? = null
    private var assetLoader: LocalStaticAssetLoader? = null
    private var server: LocalStaticFileServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = readConfig()
        val newWebView = WebView(this)
        webView = newWebView
        setContentView(createContentView(newWebView))
        newWebView.settings.javaScriptEnabled = true
        newWebView.settings.domStorageEnabled = true
        newWebView.settings.allowFileAccess = false
        newWebView.settings.allowContentAccess = false
        newWebView.webViewClient = RenderProcessHandlingWebViewClient()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateBack()
                }
            }
        )

        when (config.build.mode) {
            "asset-loader" -> loadFromAssets(newWebView, config.build)
            "http-server" -> loadFromLocalServer(newWebView, config.build)
            else -> loadingIndicator?.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        assetLoader?.stop()
        server?.stop()
        webView?.destroy()
        super.onDestroy()
    }

    private fun navigateBack() {
        val currentWebView = webView
        if (currentWebView?.canGoBack() == true) {
            currentWebView.goBack()
        } else {
            finish()
        }
    }

    private fun createContentView(webView: WebView): FrameLayout = FrameLayout(this).apply {
        addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        loadingIndicator = ProgressBar(this@ShellActivity).also { indicator ->
            addView(
                indicator,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        webView?.invalidate()
    }

    private fun readConfig(): WorkspaceConfig = assets.open("vibe.config.json").bufferedReader().use {
        configJson.decodeFromString(WorkspaceConfig.serializer(), it.readText())
    }

    private fun loadFromAssets(webView: WebView, config: WebRuntimeConfig) {
        val frontendAssets = extractFrontendAssets().toPath()
        val frontendRoot = frontendAssets.resolve(config.root).normalize()
        require(frontendRoot.startsWith(frontendAssets)) { "Build root must stay inside frontend assets" }
        val entry = frontendRoot.resolve(config.entry).normalize()
        require(entry.startsWith(frontendRoot)) { "Build entry must stay inside frontend assets" }
        val newAssetLoader = LocalStaticAssetLoader(this)
        assetLoader = newAssetLoader
        webView.webViewClient = object : RenderProcessHandlingWebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                newAssetLoader.shouldInterceptRequest(request.url)
        }
        val entryPath = frontendRoot.relativize(entry).toString().replace(File.separatorChar, '/')
        webView.loadUrl(newAssetLoader.start(frontendRoot, entryPath).toString())
    }

    private fun loadFromLocalServer(webView: WebView, config: WebRuntimeConfig) {
        val frontendAssets = extractFrontendAssets().toPath()
        val frontendRoot = frontendAssets.resolve(config.root).normalize()
        require(frontendRoot.startsWith(frontendAssets)) { "Build root must stay inside frontend assets" }
        val newServer = LocalStaticFileServer()
        server = newServer
        val endpoint = runBlocking { newServer.start(frontendRoot, 0, config.fallback) }
        webView.loadUrl(endpoint.resolve(config.entry).toString())
    }

    private fun extractFrontendAssets(): File {
        val frontendDirectory = File(cacheDir, "frontend_app")
        check(!frontendDirectory.exists() || frontendDirectory.deleteRecursively()) {
            "Unable to clear frontend asset cache"
        }
        copyAssetDirectory("frontend_app", frontendDirectory)
        return frontendDirectory
    }

    private fun copyAssetDirectory(assetPath: String, destination: File) {
        val children = assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            assets.open(assetPath).use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }

        destination.mkdirs()
        children.forEach { child -> copyAssetDirectory("$assetPath/$child", File(destination, child)) }
    }

    private companion object {
        val configJson = Json { ignoreUnknownKeys = true }
    }

    private open inner class RenderProcessHandlingWebViewClient : WebViewClient() {
        override fun onPageCommitVisible(view: WebView, url: String) {
            if (webView === view) loadingIndicator?.visibility = View.GONE
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (webView === view) webView = null
            view.destroy()
            finish()
            return true
        }
    }
}
