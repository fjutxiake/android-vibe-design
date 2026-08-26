package com.aeibi.design.apk.engine

import java.nio.file.Path

/**
 * APKEditor 引擎的解码产物布局（实测确认的结构）：
 * - manifest 在解码目录根
 * - 资源在 resources/package_1/res/
 * - root 文件（assets/lib/META-INF）在 root/
 */
class ApkEditorLayout : ApkLayout {

    override fun manifestFile(decodedDir: Path): Path = decodedDir.resolve(MANIFEST)

    override fun resRoot(decodedDir: Path): Path = decodedDir.resolve(RESOURCES).resolve(PACKAGE).resolve(RES)

    override fun rootDir(decodedDir: Path): Path = decodedDir.resolve(ROOT)

    private companion object {
        const val MANIFEST = "AndroidManifest.xml"
        const val RESOURCES = "resources"
        const val PACKAGE = "package_1"
        const val RES = "res"
        const val ROOT = "root"
    }
}
