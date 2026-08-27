package com.aeibi.design.apk.tool

import com.aeibi.design.apk.ApkIo
import java.nio.file.Files

/** 读取文件内容。 */
class ReadFileTool : ApkFileTool {

    override val name: String = "read_file"

    override val description: String = "读取明文目录中的文件内容。参数: path（相对路径）"

    override fun execute(context: ToolContext, args: ToolArgs): ToolResult {
        val target = FileToolSupport.resolve(context.decodedDir, args.path).getOrElse {
            return ToolResult.fail(it.message ?: "路径解析失败")
        }
        FileToolSupport.requireFile(target).getOrElse {
            return ToolResult.fail(it.message ?: "文件检查失败")
        }
        val content = runCatching { ApkIo.readString(target) }.getOrElse {
            return ToolResult.fail("读取失败: ${it.message}")
        }
        return ToolResult.ok(
            "读取 ${args.path}（${content.length} 字符）",
            mapOf("path" to (args.path ?: ""), "content" to content)
        )
    }
}

/** 写入文件内容（覆盖，自动创建父目录）。文本用 [ToolArgs.content]，二进制用 [ToolArgs.contentBase64]。 */
class WriteFileTool : ApkFileTool {

    override val name: String = "write_file"

    override val description: String = "写入文件内容（覆盖）。参数: path, content（文本）或 contentBase64（二进制）"

    override fun execute(context: ToolContext, args: ToolArgs): ToolResult {
        val target = FileToolSupport.resolve(context.decodedDir, args.path).getOrElse {
            return ToolResult.fail(it.message ?: "路径解析失败")
        }
        val bytes =
            when {
                args.content != null -> args.content.toByteArray(Charsets.UTF_8)
                args.contentBase64 != null -> runCatching {
                    java.util.Base64.getDecoder().decode(args.contentBase64)
                }.getOrElse { return ToolResult.fail("Base64 解码失败: ${it.message}") }
                else -> return ToolResult.fail("缺少 content 或 contentBase64 参数")
            }
        runCatching {
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }.onFailure {
            return ToolResult.fail("写入失败: ${it.message}")
        }
        context.log("write_file: ${args.path}（${bytes.size} 字节）")
        return ToolResult.ok("已写入 ${args.path}（${bytes.size} 字节）")
    }
}

/** 替换文件中的文本（全部出现处）。 */
class EditFileTool : ApkFileTool {

    override val name: String = "edit_file"

    override val description: String = "替换文件中的文本。参数: path, oldText, newText"

    override fun execute(context: ToolContext, args: ToolArgs): ToolResult {
        val target = FileToolSupport.resolve(context.decodedDir, args.path).getOrElse {
            return ToolResult.fail(it.message ?: "路径解析失败")
        }
        FileToolSupport.requireFile(target).getOrElse {
            return ToolResult.fail(it.message ?: "文件检查失败")
        }
        val oldText = args.oldText ?: return ToolResult.fail("缺少 oldText 参数")
        val newText = args.newText ?: ""
        val result = runCatching {
            val content = ApkIo.readString(target)
            val count = content.windowed(oldText.length).count { it == oldText }
            if (count == 0) {
                ToolResult.fail("未找到要替换的文本: $oldText")
            } else {
                ApkIo.writeString(target, content.replace(oldText, newText))
                context.log("edit_file: ${args.path} 替换 $count 处")
                ToolResult.ok("已替换 $count 处（${args.path}）", mapOf("replaced" to count))
            }
        }.getOrElse { ToolResult.fail("编辑失败: ${it.message}") }
        return result
    }
}

/** 删除文件或目录。 */
class DeleteFileTool : ApkFileTool {

    override val name: String = "delete_file"

    override val description: String = "删除文件或目录。参数: path"

    override fun execute(context: ToolContext, args: ToolArgs): ToolResult {
        val target = FileToolSupport.resolve(context.decodedDir, args.path).getOrElse {
            return ToolResult.fail(it.message ?: "路径解析失败")
        }
        if (!Files.exists(target)) return ToolResult.fail("路径不存在: ${args.path}")
        runCatching {
            target.toFile().deleteRecursively()
        }.onFailure {
            return ToolResult.fail("删除失败: ${it.message}")
        }
        context.log("delete_file: ${args.path}")
        return ToolResult.ok("已删除 ${args.path}")
    }
}

/** 列出目录内容。 */
class ListFilesTool : ApkFileTool {

    override val name: String = "list_files"

    override val description: String = "列出目录内容。参数: path（默认根目录）, recursive"

    override fun execute(context: ToolContext, args: ToolArgs): ToolResult {
        val target = FileToolSupport.resolve(context.decodedDir, args.path ?: ".").getOrElse {
            return ToolResult.fail(it.message ?: "路径解析失败")
        }
        if (!Files.isDirectory(target)) return ToolResult.fail("不是目录: ${args.path ?: "."}")

        val entries = if (args.recursive) {
            ApkIo.walk(target)
                .filter { it != target }
                .map { target.relativize(it).toString() }
                .toList()
        } else {
            ApkIo.list(target).map { it.fileName.toString() }.sorted()
        }
        return ToolResult.ok("列出 ${entries.size} 项", mapOf("entries" to entries))
    }
}
