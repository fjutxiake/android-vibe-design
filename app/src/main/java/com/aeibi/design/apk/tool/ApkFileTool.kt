package com.aeibi.design.apk.tool

import java.nio.file.Path

/**
 * APK 明文文件操作工具接口——原子能力，仿 Claude Code 的 tool 模型。
 *
 * 工具只认识解码后的明文目录（[ToolContext.decodedDir]），
 * 提供 read/edit/write/delete/list 等基础文件能力；
 * 引擎（decode/build）与工具层完全解耦——引擎不管"怎么改"，
 * 工具不管"怎么转换"。
 *
 * 未来 Agent 的 tool-use 循环直接调用这些工具操作项目文件。
 */
interface ApkFileTool {

    /** 工具名（如 "edit_file"），Agent 调用时的标识。 */
    val name: String

    /** 工具说明（给 Agent 看的描述，说明用途与参数）。 */
    val description: String

    /** 执行工具。 */
    fun execute(context: ToolContext, args: ToolArgs): ToolResult
}

/** 工具执行上下文：明文目录 + 根目录的引擎布局信息。 */
class ToolContext(val decodedDir: Path, val log: (String) -> Unit = {})

/** 统一参数模型（各工具按需取用字段）。 */
data class ToolArgs(
    /** 相对明文目录的路径。 */
    val path: String? = null,
    /** 写入内容（write_file 文本模式）。 */
    val content: String? = null,
    /** 写入内容（write_file 二进制模式，Base64 编码）。 */
    val contentBase64: String? = null,
    /** 替换目标文本（edit_file）。 */
    val oldText: String? = null,
    /** 替换值（edit_file）。 */
    val newText: String? = null,
    /** 是否递归（list_files）。 */
    val recursive: Boolean = false,
    /** 行号上限（read_file 读取行数）。 */
    val limit: Int = -1
)

/** 工具执行结果。 */
data class ToolResult(val success: Boolean, val message: String, val data: Map<String, Any>? = null) {
    companion object {
        fun ok(message: String, data: Map<String, Any>? = null): ToolResult =
            ToolResult(success = true, message = message, data = data)

        fun fail(message: String): ToolResult = ToolResult(success = false, message = message)
    }
}
