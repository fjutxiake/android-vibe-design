package com.aeibi.design.apk.model

import java.nio.file.Path

/**
 * 一次 APK 构建的完整输入。
 *
 * 所有字段均可选：null/默认值表示"保持模板壳不变"，操作按需生效——
 * 例如只改包名时，其余字段全部留默认即可。
 */
data class ApkBuildRequest(
    /** 预编译 WebView 壳 APK（模板）路径。 */
    val templateApk: Path,
    /** 最终产出 APK 路径。 */
    val outputApk: Path,
    /** 新包名（如 com.example.toy）。null = 保持模板。 */
    val packageName: String? = null,
    /** 新应用名。null = 保持模板。 */
    val appLabel: String? = null,
    /** 应用图标文件（png/webp）。null = 保持模板图标。 */
    val iconFile: Path? = null,
    /** 前端产物目录（静态 HTML/CSS/JS，注入到 assets/frontend_app/）。null = 不注入。 */
    val frontendDir: Path? = null,
    /** 写入 assets/app_config.json 的配置项。null = 不写入。 */
    val config: Map<String, String>? = null,
    /** versionCode / versionName。null = 保持模板。 */
    val versionCode: Int? = null,
    val versionName: String? = null,
    /** 保留的 ABI（如 setOf("arm64-v8a")）。null/空 = 保留全部。 */
    val abiWhitelist: Set<String>? = null
)
