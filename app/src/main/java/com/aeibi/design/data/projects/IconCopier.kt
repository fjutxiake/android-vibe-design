package com.aeibi.design.data.projects

import java.io.File

/** 把 [uri] 的内容拷贝进 [projectDir],返回图标文件名(如 "icon.png");无图标或失败返回 null。 */
fun interface IconCopier {
    fun copy(uri: String?, projectDir: File): String?
}
