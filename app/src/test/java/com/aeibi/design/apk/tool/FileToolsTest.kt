package com.aeibi.design.apk.tool

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileToolsTest {

    private val root: Path = Files.createTempDirectory("tools")

    private fun context(): ToolContext = ToolContext(decodedDir = root)

    @Test
    fun `read_file-读取内容`() {
        Files.createDirectories(root.resolve("assets"))
        Files.writeString(root.resolve("assets/config.json"), "{\"a\":1}")
        val result = ReadFileTool().execute(context(), ToolArgs(path = "assets/config.json"))

        assertTrue(result.success)
        assertEquals("{\"a\":1}", result.data?.get("content"))
    }

    @Test
    fun `write_file-写入并创建父目录`() {
        val result = WriteFileTool().execute(
            context(),
            ToolArgs(path = "assets/frontend_app/index.html", content = "<html/>")
        )

        assertTrue(result.success)
        assertTrue(Files.exists(root.resolve("assets/frontend_app/index.html")))
        assertEquals("<html/>", Files.readString(root.resolve("assets/frontend_app/index.html")))
    }

    @Test
    fun `edit_file-替换文本并报告次数`() {
        Files.createDirectories(root.resolve("assets"))
        Files.writeString(root.resolve("assets/colors.css"), "color: red; border: red;")
        val result = EditFileTool().execute(
            context(),
            ToolArgs(path = "assets/colors.css", oldText = "red", newText = "blue")
        )

        assertTrue(result.success)
        assertEquals(2, result.data?.get("replaced"))
        assertEquals("color: blue; border: blue;", Files.readString(root.resolve("assets/colors.css")))
    }

    @Test
    fun `edit_file-未找到文本时报错`() {
        Files.createDirectories(root.resolve("assets"))
        Files.writeString(root.resolve("assets/a.txt"), "hello")
        val result = EditFileTool().execute(context(), ToolArgs(path = "assets/a.txt", oldText = "nope", newText = "x"))

        assertFalse(result.success)
    }

    @Test
    fun `delete_file-删除文件`() {
        Files.createDirectories(root.resolve("assets"))
        Files.writeString(root.resolve("assets/old.txt"), "x")
        val result = DeleteFileTool().execute(context(), ToolArgs(path = "assets/old.txt"))

        assertTrue(result.success)
        assertFalse(Files.exists(root.resolve("assets/old.txt")))
    }

    @Test
    fun `list_files-列出与递归`() {
        Files.createDirectories(root.resolve("a/b"))
        Files.writeString(root.resolve("a/one.txt"), "1")
        Files.writeString(root.resolve("a/b/two.txt"), "2")

        val flat = ListFilesTool().execute(context(), ToolArgs(path = "a"))
        val flatEntries = (flat.data?.get("entries") as List<*>).map { it.toString() }.sorted()
        assertEquals(listOf("b", "one.txt"), flatEntries)

        val recursive = ListFilesTool().execute(context(), ToolArgs(path = "a", recursive = true))
        // 递归包含子目录 b + 2 个文件 = 3 项
        assertEquals(3, (recursive.data?.get("entries") as List<*>).size)
    }

    @Test
    fun `路径穿越-拒绝目录外访问`() {
        Files.writeString(Files.createTempDirectory("outside").resolve("secret.txt"), "s")
        val result = ReadFileTool().execute(context(), ToolArgs(path = "../outside/secret.txt"))

        assertFalse(result.success)
    }
}
