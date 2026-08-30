package com.aeibi.design.feature.preview

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.aeibi.design.R

/**
 * 项目预览（mode: static 最小实现）——WebViewAssetLoader 拦截器加载静态内容，
 * 无服务器、无端口。当前加载内置演示页（assets/frontend_app/）；
 * 产物预览接入时，将 [AssetsPathHandler] 替换为读取产物目录的 PathHandler 即可。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProjectPreviewScreen(
    projectId: String,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onFullscreenClick: () -> Unit = {}
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preview_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onFullscreenClick) {
                        Icon(
                            Icons.Filled.Fullscreen,
                            contentDescription = stringResource(R.string.preview_cd_fullscreen)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            StaticContentWebView(
                modifier = Modifier.fillMaxSize(),
                // 演示页路径；未来产物预览指向产物目录（assets/frontend_app/index.html 或注入目录）
                initialUrl = DEMO_URL
            )
        }
    }

    // projectId 暂未使用（预览数据源接入时用于定位项目产物目录）
}

/**
 * 静态内容 WebView：WebViewAssetLoader 拦截 `https://appassets.androidplatform.net/` 请求，
 * 从 assets/ 返回内容——完全本地，无服务器。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun StaticContentWebView(modifier: Modifier = Modifier, initialUrl: String) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
            WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
                }
                loadUrl(initialUrl)
            }
        }
    )
}

private const val DEMO_URL = "https://appassets.androidplatform.net/assets/frontend_app/index.html"
