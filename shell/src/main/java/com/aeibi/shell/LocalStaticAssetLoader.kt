package com.aeibi.shell

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.core.net.toUri
import androidx.webkit.WebViewAssetLoader
import java.nio.file.Path

class LocalStaticAssetLoader(context: Context) {

    private val applicationContext = context.applicationContext
    private var assetLoader: WebViewAssetLoader? = null

    fun start(rootDirectory: Path, entryFile: String = "index.html"): Uri {
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
        return BASE_URL.toUri().buildUpon()
            .appendEncodedPath(Uri.encode(entryFile, "/"))
            .build()
    }

    fun shouldInterceptRequest(uri: Uri): WebResourceResponse? = assetLoader?.shouldInterceptRequest(uri)

    fun stop() {
        assetLoader = null
    }

    private companion object {
        const val BASE_URL = "https://appassets.androidplatform.net/"
    }
}
