package com.aeibi.shell

import android.app.Activity
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class ShellActivity : Activity() {
    private var webView: WebView? = null
    private var server: LocalStaticFileServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = readConfig()
        val newWebView = WebView(this)
        webView = newWebView
        setContentView(newWebView)
        configureWebView(newWebView)

        when (config.build.mode) {
            "asset-loader" -> loadFromAssets(newWebView, config.build)
            "http-server" -> loadFromLocalServer(newWebView, config.build)
            else -> error("Unsupported build mode: ${config.build.mode}")
        }
    }

    override fun onDestroy() {
        server?.stop()
        webView?.destroy()
        super.onDestroy()
    }

    private fun readConfig(): WorkspaceConfig = assets.open("vibe.config.json").bufferedReader().use {
        configJson.decodeFromString(WorkspaceConfig.serializer(), it.readText())
    }

    private fun configureWebView(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
    }

    private fun loadFromAssets(webView: WebView, config: WebRuntimeConfig) {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)
        }
        webView.loadUrl("https://appassets.androidplatform.net/assets/frontend_app/${config.entry}")
    }

    private fun loadFromLocalServer(webView: WebView, config: WebRuntimeConfig) {
        val frontendDirectory = extractFrontendAssets()
        val newServer = LocalStaticFileServer()
        server = newServer
        val endpoint = runBlocking { newServer.start(frontendDirectory.toPath(), 0, config.fallback) }
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
}
