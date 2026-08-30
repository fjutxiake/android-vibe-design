package com.aeibi.design.apk.engine

import android.content.Context
import com.reandroid.apk.AndroidFrameworks
import com.reandroid.apk.ApkModule
import com.reandroid.apk.ApkModuleXmlDecoder
import com.reandroid.apk.ApkModuleXmlEncoder
import java.nio.file.Path

/**
 * Android 端引擎：基于 ARSCLib 纯 Java 实现，零外部进程。
 *
 * - 解码：ApkModule → ApkModuleXmlDecoder（manifest/资源 → 明文；dex 原样字节）
 * - 重打包：ApkModuleXmlEncoder → writeApk（自实现 ZIP 写入器，含 4 字节对齐）
 *
 * framework 资源从 App assets 内置加载（[AssetsFrameworkManager]）。
 */
class AndroidApkEngine(context: Context) : ApkEngine {

    init {
        AndroidFrameworks.setFrameworkManager(AssetsFrameworkManager(context))
    }

    override fun decode(sourceApk: Path, destDir: Path) {
        ApkModule.loadApkFile(sourceApk.toFile()).use { module ->
            // framework 由 AssetsFrameworkManager 提供（属性名解析必需，勿关闭自动加载）
            val decoder = ApkModuleXmlDecoder(module)
            decoder.decode(destDir.toFile())
        }
    }

    override fun build(decodedDir: Path, outApk: Path): BuildSummary {
        val encoder = ApkModuleXmlEncoder()
        encoder.scanDirectory(decodedDir.toFile())
        encoder.getApkModule().use { module ->
            module.writeApk(outApk.toFile(), null)
        }
        return summarize(outApk)
    }

    /** 读取产物 ZIP 生成自检摘要。 */
    private fun summarize(outApk: Path): BuildSummary {
        val entries = mutableListOf<String>()
        java.util.zip.ZipFile(outApk.toFile()).use { zip ->
            zip.entries().asSequence().forEach { entries += it.name }
        }
        val dexCount = entries.count { it.endsWith(".dex") }
        val frontendEntries = entries.filter { it.startsWith(FRONTEND_PREFIX) }
        return BuildSummary(
            apkSizeBytes = outApk.toFile().length(),
            entryCount = entries.size,
            hasDex = dexCount > 0,
            dexCount = dexCount,
            hasFrontendAssets = frontendEntries.isNotEmpty(),
            frontendFileCount = frontendEntries.size
        )
    }

    private companion object {
        const val FRONTEND_PREFIX = "assets/frontend_app/"
    }
}
