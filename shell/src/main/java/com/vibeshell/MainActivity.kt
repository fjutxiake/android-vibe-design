package com.vibeshell

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * WebView 壳入口：加载注入的前端产物（assets/frontend_app/index.html）。
 *
 * 前端代码在构建时由主应用注入；本壳只负责渲染，不做任何业务逻辑。
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl(ENTRY_URL)
        }
        setContentView(webView)
    }

    // 壳保持最小依赖，暂用已弃用 API；迁移 OnBackPressedDispatcher 需引入 androidx.activity
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private companion object {
        const val ENTRY_URL = "file:///android_asset/frontend_app/index.html"
    }
}
