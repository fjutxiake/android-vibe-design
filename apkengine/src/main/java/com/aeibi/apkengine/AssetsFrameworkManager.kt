package com.aeibi.apkengine

import android.content.Context
import com.reandroid.apk.FrameworkApk
import com.reandroid.apk.framework.FrameworkManager
import java.io.File

/**
 * 从 App assets 加载内置 framework 资源（android-23 ~ android-36）。
 *
 * ARSCLib 桌面版从 classpath 读取 frameworks，Android 上 classpath 不可用，
 * 故把 frameworks 打包进 assets，经此 Manager 注册给 AndroidFrameworks。
 */
class AssetsFrameworkManager(private val context: Context) : FrameworkManager() {

    private val versions: List<Int> = FRAMEWORK_VERSIONS

    override fun get(version: Int): FrameworkApk? {
        if (version !in versions) return null
        return load(version)
    }

    override fun getBestMatch(version: Int): FrameworkApk? {
        val nearest = getNearestVersion(version) ?: return null
        return load(nearest)
    }

    override fun getNearestVersion(version: Int): Int? {
        if (version in versions) return version
        val lower = versions.filter { it <= version }.maxOrNull()
        val higher = versions.filter { it >= version }.minOrNull()
        return when {
            lower == null -> higher
            higher == null -> lower
            version - lower <= higher - version -> lower
            else -> higher
        }
    }

    override fun getLatestVersion(): Int? = versions.maxOrNull()

    override fun getLatest(): FrameworkApk? {
        val latest = getLatestVersion() ?: return null
        return load(latest)
    }

    private fun load(version: Int): FrameworkApk? = runCatching {
        // 先解压到 cache 再用文件加载（流式加载在 Android 上不可靠）
        val cacheDir = File(context.cacheDir, "frameworks").apply { mkdirs() }
        val apkFile = File(cacheDir, "android-$version.apk")
        if (!apkFile.exists()) {
            context.assets.open("frameworks/android/android-$version.apk").use { input ->
                apkFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        FrameworkApk.loadApkFile(apkFile).also {
            setCurrent(it)
        }
    }.onFailure { error ->
        android.util.Log.e(TAG, "framework $version 加载失败", error)
    }.getOrNull()

    private companion object {
        const val TAG = "AssetsFrameworkManager"
        val FRAMEWORK_VERSIONS: List<Int> = (23..36).toList()
    }
}
