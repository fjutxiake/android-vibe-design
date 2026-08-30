package com.aeibi.design.feature.preview

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.nio.file.Path

/**
 * 静态内容加载器（mode: static）——用 WebViewAssetLoader 拦截
 * `https://appassets.androidplatform.net/` 请求，从指定目录返回内容。
 *
 * 无服务器、无端口、无网络栈；生命周期与 [LocalStaticFileServer] 对称
 * （start / stop / shouldInterceptRequest）。
 *
 * 用法：
 * ```
 * val loader = LocalStaticAssetLoader(context)
 * val uri = loader.start(rootDirectory)          // https://appassets.androidplatform.net/
 * webView.webViewClient = ... { loader.shouldInterceptRequest(request.url) }
 * ...
 * loader.stop()
 * ```
 */
class LocalStaticAssetLoader(context: Context) {

    private val applicationContext = context.applicationContext
    private var assetLoader: WebViewAssetLoader? = null

    fun start(rootDirectory: Path): Uri {
        check(assetLoader == null) { "Static asset loader is already running" }
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                "/",
                WebViewAssetLoader.InternalStoragePathHandler(
                    applicationContext,
                    rootDirectory.toFile()
                )
            )
            .build()
        return Uri.parse(BASE_URL)
    }

    fun shouldInterceptRequest(uri: Uri): WebResourceResponse? = assetLoader?.shouldInterceptRequest(uri)

    fun stop() {
        assetLoader = null
    }

    private companion object {
        const val BASE_URL = "https://appassets.androidplatform.net/"
    }
}
