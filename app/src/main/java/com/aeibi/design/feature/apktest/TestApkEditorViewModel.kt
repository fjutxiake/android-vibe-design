package com.aeibi.design.feature.apktest

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeibi.design.apk.ApkPipeline
import com.aeibi.design.apk.BuildLogger
import com.aeibi.design.apk.model.ApkBuildRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 临时调试用状态。 */
data class TestApkEditorUiState(
    val templateName: String? = null,
    val templatePath: String? = null,
    val packageName: String = "com.vibetest.demo",
    val appLabel: String = "玩具测试",
    val frontendName: String? = null,
    val frontendPath: String? = null,
    val isBuilding: Boolean = false,
    val isPreviewingStructure: Boolean = false,
    val structure: List<String>? = null,
    val logs: List<String> = emptyList(),
    val result: String? = null,
    val error: String? = null
)

/**
 * 临时调试 ViewModel：手动触发 APK 手术链路（引擎/操作由 Hilt 声明式装配）。
 * 正式版移除本类。
 */
@SuppressLint("NewApi") // 临时调试 UI：MediaStore.Downloads 需 API 29，正式版移除
@HiltViewModel
class TestApkEditorViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val pipeline: ApkPipeline,
    private val decoder: com.aeibi.design.apk.engine.ApkDecoder
) : ViewModel() {

    private val _uiState = MutableStateFlow(TestApkEditorUiState())
    val uiState: StateFlow<TestApkEditorUiState> = _uiState.asStateFlow()

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // 恢复上次选择的模板（持久化）
        val savedPath = prefs.getString(KEY_TEMPLATE_PATH, null)
        val savedName = prefs.getString(KEY_TEMPLATE_NAME, null)
        if (savedPath != null && File(savedPath).exists()) {
            _uiState.update {
                it.copy(templateName = savedName, templatePath = savedPath)
            }
        }
    }

    /** 外部日志文件路径（Download/vibe-design-log.txt，模拟器可见）。 */
    private val _logPath = MutableStateFlow<String?>(null)
    val logPath: StateFlow<String?> = _logPath.asStateFlow()

    /** 已创建的日志文件 uri（进程内复用，避免产生多个日志文件）。 */
    private var logUri: Uri? = null

    fun selectTemplate(uri: Uri) {
        logToFile("selectTemplate 被调用: uri=$uri")
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            withContext(Dispatchers.IO) {
                runCatching {
                    val work = File(appContext.filesDir, "apk-test").apply { mkdirs() }
                    val target = File(work, "template-${UUID.randomUUID()}.apk")
                    val input = appContext.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("无法打开所选文件（uri=$uri）")
                    input.use { inputStream ->
                        target.outputStream().use { output -> inputStream.copyTo(output) }
                    }
                    val name = uri.lastPathSegment ?: "template.apk"
                    logToFile("模板拷贝成功: $name -> ${target.absolutePath}（${target.length()} 字节）")
                    // 持久化模板选择（App 重启后恢复）
                    prefs.edit()
                        .putString(KEY_TEMPLATE_PATH, target.absolutePath)
                        .putString(KEY_TEMPLATE_NAME, name)
                        .apply()
                    _uiState.update {
                        it.copy(templateName = name, templatePath = target.absolutePath)
                    }
                }.onFailure { error ->
                    logToFile("模板选择失败: ${error.stackTraceToString()}")
                    _uiState.update { it.copy(error = "模板选择失败: ${error.message}") }
                }
            }
        }
    }

    /** 选择器被取消（uri == null）。 */
    fun onTemplatePickCancelled() {
        _uiState.update { it.copy(error = "未选择模板（选择器被取消）") }
    }

    /** 选择前端代码目录（整目录导入，注入 assets/frontend_app/）。 */
    fun selectFrontend(uri: Uri) {
        logToFile("selectFrontend 被调用: uri=$uri")
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(appContext.filesDir, "frontend-${UUID.randomUUID()}").apply { mkdirs() }
                    val count = copyTree(appContext, uri, target)
                    if (count == 0) throw IllegalStateException("所选目录为空或无法读取")
                    logToFile("前端导入成功: $count 个文件 -> ${target.absolutePath}")
                    _uiState.update {
                        it.copy(frontendName = uri.lastPathSegment ?: "frontend", frontendPath = target.absolutePath)
                    }
                }.onFailure { error ->
                    logToFile("前端导入失败: ${error.stackTraceToString()}")
                    _uiState.update { it.copy(error = "前端导入失败: ${error.message}") }
                }
            }
        }
    }

    /** 递归拷贝 SAF 目录树到本地目录，返回文件数。 */
    private fun copyTree(context: Context, uri: Uri, target: File): Int {
        val documentFile = android.provider.DocumentsContract.buildDocumentUriUsingTree(
            uri,
            android.provider.DocumentsContract.getTreeDocumentId(uri)
        )
        var count = 0
        fun copyDir(dirUri: Uri, dest: File) {
            val dir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, dirUri) ?: return
            dir.listFiles().forEach { child ->
                if (child.isDirectory) {
                    val sub = File(dest, child.name ?: return@forEach).apply { mkdirs() }
                    copyDir(child.uri, sub)
                } else {
                    val name = child.name ?: return@forEach
                    context.contentResolver.openInputStream(child.uri)?.use { input ->
                        File(dest, name).outputStream().use { output -> input.copyTo(output) }
                        count++
                    }
                }
            }
        }
        copyDir(uri, target)
        return count
    }

    /** 预览模板的文件结构（解码后显示目录树）。 */
    fun previewStructure() {
        val template = _uiState.value.templatePath ?: return
        if (_uiState.value.isPreviewingStructure) return
        logToFile("previewStructure 被调用: template=$template")
        viewModelScope.launch {
            _uiState.update { it.copy(isPreviewingStructure = true, structure = null) }
            withContext(Dispatchers.IO) {
                runCatching {
                    val decodedDir = File(appContext.cacheDir, "preview-decoded").apply {
                        deleteRecursively()
                        mkdirs()
                    }
                    decoder.decode(File(template).toPath(), decodedDir.toPath())
                    val tree = buildFileTree(decodedDir, maxLines = MAX_STRUCTURE_LINES)
                    logToFile("结构预览完成: ${tree.size} 行")
                    _uiState.update {
                        it.copy(isPreviewingStructure = false, structure = tree)
                    }
                }.onFailure { error ->
                    logToFile("结构预览失败: ${error.stackTraceToString()}")
                    _uiState.update {
                        it.copy(isPreviewingStructure = false, error = "结构预览失败: ${error.message}")
                    }
                }
            }
        }
    }

    /** 生成缩进文件树（目录 + 文件，限制行数）。 */
    private fun buildFileTree(root: File, maxLines: Int): List<String> {
        val lines = mutableListOf<String>()
        val maxDepth = 4
        fun walk(dir: File, depth: Int) {
            if (lines.size >= maxLines) return
            if (depth > maxDepth) return
            dir.listFiles()?.sortedBy { file ->
                if (file.isDirectory) "0${file.name}" else "1${file.name}"
            }?.forEach { file ->
                if (lines.size >= maxLines) return
                val indent = "  ".repeat(depth)
                val mark = if (file.isDirectory) "[D] " else "    "
                lines += "$indent$mark${file.name}"
                if (file.isDirectory) walk(file, depth + 1)
            }
        }
        lines += "[root] ${root.name}"
        walk(root, 1)
        if (lines.size >= maxLines) lines += "...（已达显示上限 $maxLines 行）"
        return lines
    }

    fun updatePackageName(value: String) = _uiState.update { it.copy(packageName = value) }

    fun updateAppLabel(value: String) = _uiState.update { it.copy(appLabel = value) }

    fun build() {
        val state = _uiState.value
        val template = state.templatePath ?: run {
            logToFile("build 被调用但模板未选择")
            return
        }
        if (state.isBuilding) return
        logToFile("build 被调用: template=$template, package=${state.packageName}, label=${state.appLabel}")

        viewModelScope.launch {
            _uiState.update { it.copy(isBuilding = true, logs = emptyList(), result = null, error = null) }
            val logs = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                runCatching {
                    val output = File(appContext.filesDir, "apk-test-output").apply { mkdirs() }
                        .resolve("output-${UUID.randomUUID()}.apk")
                    val request = ApkBuildRequest(
                        templateApk = File(template).toPath(),
                        outputApk = output.toPath(),
                        packageName = state.packageName,
                        appLabel = state.appLabel,
                        frontendDir = state.frontendPath?.let { File(it).toPath() }
                    )
                    val result = pipeline.build(
                        request,
                        logger = BuildLogger { stage, message -> logs += "[${stage.name}] $message" }
                    )

                    // 产物复制到可见位置：Downloads（模拟器内可安装）+ 共享目录（电脑可见）
                    val visiblePath = exportOutput(output)
                    logToFile("构建完成: ${output.absolutePath}（${output.length()} 字节）→ $visiblePath")
                    _uiState.update {
                        it.copy(
                            isBuilding = false,
                            logs = logs,
                            result = buildString {
                                append("输出: $visiblePath（${output.length() / 1024} KB）")
                                append("\n验证: ")
                                append(if (result.verification.passed) "通过" else result.verification.issues)
                            }
                        )
                    }
                }.onFailure { error ->
                    logToFile("构建失败: ${error.stackTraceToString()}")
                    _uiState.update {
                        it.copy(isBuilding = false, logs = logs, error = "构建失败: ${error.message}")
                    }
                }
            }
        }
    }

    /** 产物输出到可见位置，返回用户可访问的路径描述。 */
    private fun exportOutput(output: File): String {
        val destinations = mutableListOf<String>()
        // 1. 共享文件夹（电脑直接可见）
        val sharedDirs = listOf(
            File("/storage/emulated/0/MuMuShared"),
            File("/mnt/shared"),
            File("/sdcard/MuMuShared")
        )
        for (dir in sharedDirs) {
            runCatching {
                dir.mkdirs()
                if (dir.canWrite()) {
                    val target = File(dir, "output.apk")
                    output.copyTo(target, overwrite = true)
                    destinations += target.absolutePath
                    return destinations.joinToString(" / ")
                }
            }
        }
        // 2. MediaStore Downloads（模拟器内可见可安装）
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "vibe-design-output.apk")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return output.absolutePath
        resolver.openOutputStream(uri)?.use { stream ->
            output.inputStream().use { input -> input.copyTo(stream) }
        }
        destinations += "Download/vibe-design-output.apk"
        return destinations.joinToString(" / ")
    }

    /**
     * 追加写外部日志。优先写 MuMu 共享文件夹（模拟器内挂载点），
     * 失败回退 MediaStore Downloads。失败静默（日志不应影响主流程）。
     */
    private fun logToFile(message: String) {
        val line = "${System.currentTimeMillis()} $message\n"
        try {
            val sharedDirs = listOf(
                File("/storage/emulated/0/MuMuShared"),
                File("/mnt/shared"),
                File("/sdcard/MuMuShared")
            )
            for (dir in sharedDirs) {
                runCatching {
                    dir.mkdirs()
                    if (!dir.canWrite()) return@runCatching
                    File(dir, LOG_FILE_NAME).appendText(line)
                    _logPath.value = "${dir.absolutePath}/$LOG_FILE_NAME"
                    return
                }
            }
            // 回退：MediaStore Downloads
            writeToDownloads(line)
        } catch (error: Throwable) {
            // 静默：日志写失败不影响主流程
        }
    }

    private fun writeToDownloads(line: String) {
        val resolver = appContext.contentResolver
        val uri = logUri ?: findOrCreateLogFile(resolver)
        if (uri != null) {
            logUri = uri
            resolver.openOutputStream(uri, "wa")?.use { stream -> stream.write(line.toByteArray()) }
            _logPath.value = "Download/$LOG_FILE_NAME"
        }
    }

    /** 在 Downloads 中查找已有日志文件，无则创建（复用同一文件，避免多个日志）。 */
    private fun findOrCreateLogFile(resolver: android.content.ContentResolver): Uri? {
        val query = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null
        )
        query?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                if (name == LOG_FILE_NAME || name.startsWith("$LOG_FILE_NAME.")) {
                    return android.content.ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        cursor.getLong(0)
                    )
                }
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, LOG_FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }

    private companion object {
        const val LOG_FILE_NAME = "vibe-design-log.txt"
        const val PREFS_NAME = "apk_test"
        const val KEY_TEMPLATE_PATH = "template_path"
        const val KEY_TEMPLATE_NAME = "template_name"
        const val MAX_STRUCTURE_LINES = 300
    }
}
