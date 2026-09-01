package com.aeibi.design.data.projectfiles

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectFileToolsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @get:Rule
    val outside = TemporaryFolder()

    private fun tools(): ProjectFileTools = ProjectFileTools(tmp.root, UnconfinedTestDispatcher())

    private fun write(path: String, content: String) {
        val file = File(tmp.root, path)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private fun writeLines(path: String, count: Int) {
        write(path, (0 until count).joinToString("\n") { "line$it" })
    }

    private fun <T> assertFailure(result: FileToolResult<T>, expected: FileToolErrorCode) {
        assertTrue(
            "Expected failure $expected, but got $result",
            result is FileToolResult.Failure && result.error.code == expected
        )
    }

    private fun <T> assertSuccess(result: FileToolResult<T>): T =
        (result as? FileToolResult.Success)?.value ?: error("Expected success, but got $result")

    // region path safety

    @Test
    fun read_parentTraversal_isRejected() = runTest {
        write("secret.txt", "secret")

        val result = tools().read("../secret.txt")

        assertFailure(result, FileToolErrorCode.PATH_ESCAPE)
    }

    @Test
    fun read_absolutePath_isRejected() = runTest {
        val result = tools().read(tmp.root.absolutePath)

        assertFailure(result, FileToolErrorCode.INVALID_PATH)
    }

    @Test
    fun read_unixStyleAbsolutePath_isRejected() = runTest {
        val result = tools().read("/etc/passwd")

        assertFailure(result, FileToolErrorCode.INVALID_PATH)
    }

    @Test
    fun read_blankPath_isRejected() = runTest {
        val result = tools().read("   ")

        assertFailure(result, FileToolErrorCode.INVALID_PATH)
    }

    @Test
    fun read_symlinkEscapingWorkspace_isRejected() = runTest {
        val secret = File(outside.root, "secret.txt").apply { writeText("secret") }
        try {
            Files.createSymbolicLink(File(tmp.root, "link.txt").toPath(), secret.toPath())
        } catch (error: Exception) {
            assumeTrue("Symlinks unavailable on this platform: ${error.message}", false)
        }

        val result = tools().read("link.txt")

        assertFailure(result, FileToolErrorCode.PATH_ESCAPE)
    }

    // endregion

    // region list

    @Test
    fun list_returnsEntriesSortedDirectoriesFirst() = runTest {
        write("b.txt", "b")
        write("a.txt", "a")
        File(tmp.root, "zdir").mkdirs()

        val result = assertSuccess(tools().list("."))

        assertEquals(listOf("zdir", "a.txt", "b.txt"), result.map { it.name })
        assertTrue(result[0].isDirectory)
        assertEquals(0, result[0].size)
        assertFalse(result[1].isDirectory)
        assertEquals("a.txt", result[1].path)
        assertEquals(1, result[1].size)
        assertTrue(result[1].modifiedAt > 0)
    }

    @Test
    fun list_emptyDirectory_returnsEmptyList() = runTest {
        val result = assertSuccess(tools().list("."))

        assertTrue(result.isEmpty())
    }

    @Test
    fun list_missingPath_isNotFound() = runTest {
        val result = tools().list("missing")

        assertFailure(result, FileToolErrorCode.NOT_FOUND)
    }

    // endregion

    // region read

    @Test
    fun read_returnsContent() = runTest {
        write("hello.txt", "hello world")

        val result = assertSuccess(tools().read("hello.txt"))

        assertEquals("hello world", result.content)
        assertFalse(result.truncated)
    }

    @Test
    fun read_withOffsetAndLimit_returnsLineRange() = runTest {
        write("lines.txt", "one\ntwo\nthree\nfour\nfive")

        val middle = assertSuccess(tools().read("lines.txt", offset = 2, limit = 2))

        assertEquals("two\nthree", middle.content)
        assertTrue(middle.truncated)

        val tail = assertSuccess(tools().read("lines.txt", offset = 4))

        assertEquals("four\nfive", tail.content)
        assertFalse(tail.truncated)
    }

    @Test
    fun read_longFile_isTruncatedAtMaxLines() = runTest {
        writeLines("long.txt", 2005)

        val result = assertSuccess(tools().read("long.txt"))

        assertEquals(2000, result.content.split("\n").size)
        assertEquals("line0", result.content.lines().first())
        assertEquals("line1999", result.content.lines().last())
        assertTrue(result.truncated)
    }

    @Test
    fun read_offsetPastEnd_returnsEmptyContent() = runTest {
        write("short.txt", "one\ntwo")

        val result = assertSuccess(tools().read("short.txt", offset = 10))

        assertEquals("", result.content)
        assertFalse(result.truncated)
    }

    @Test
    fun read_missingFile_isNotFound() = runTest {
        val result = tools().read("missing.txt")

        assertFailure(result, FileToolErrorCode.NOT_FOUND)
    }

    @Test
    fun read_directory_isNotText() = runTest {
        File(tmp.root, "subdir").mkdirs()

        val result = tools().read("subdir")

        assertFailure(result, FileToolErrorCode.NOT_TEXT)
    }

    @Test
    fun read_binaryExtension_isNotText() = runTest {
        write("image.png", "fake image data")

        val result = tools().read("image.png")

        assertFailure(result, FileToolErrorCode.NOT_TEXT)
    }

    @Test
    fun read_nullByteContent_isNotText() = runTest {
        write("data.txt", "text" + 0.toChar() + "binary")

        val result = tools().read("data.txt")

        assertFailure(result, FileToolErrorCode.NOT_TEXT)
    }

    @Test
    fun read_tooLargeFile_isTooLarge() = runTest {
        write("big.txt", "a".repeat(1_000_001))

        val result = tools().read("big.txt")

        assertFailure(result, FileToolErrorCode.TOO_LARGE)
    }

    // endregion

    // region find

    @Test
    fun find_matchesGlobAcrossDirectories() = runTest {
        write("src/app.js", "x")
        write("src/index.css", "y")
        write("src/components/Button.js", "z")
        write("README.md", "r")

        val result = assertSuccess(tools().find("**/*.js", "src"))

        assertEquals(setOf("src/app.js", "src/components/Button.js"), result.toSet())
    }

    @Test
    fun find_singleSegmentGlob_staysInDirectory() = runTest {
        write("src/app.js", "x")
        write("src/components/Button.js", "z")

        val result = assertSuccess(tools().find("*.js", "src"))

        assertEquals(listOf("src/app.js"), result)
    }

    @Test
    fun find_missingPath_isNotFound() = runTest {
        val result = tools().find("*.kt", "missing")

        assertFailure(result, FileToolErrorCode.NOT_FOUND)
    }

    // endregion

    // region search

    @Test
    fun search_returnsPathLineAndText() = runTest {
        write("notes.txt", "first line\nsecond match line\nthird line")

        val result = assertSuccess(tools().search("match", "."))

        assertEquals(1, result.size)
        assertEquals("notes.txt", result[0].path)
        assertEquals(2, result[0].line)
        assertEquals("second match line", result[0].lineText)
    }

    @Test
    fun search_tooManyResults_isRejected() = runTest {
        write("results.txt", (0 until 201).joinToString("\n") { "match line $it" })

        val result = tools().search("match", ".")

        assertFailure(result, FileToolErrorCode.TOO_MANY_RESULTS)
    }

    @Test
    fun search_skipsBinaryFiles() = runTest {
        write("notes.txt", "hello match here")
        File(tmp.root, "data.bin").writeText("match in binary")

        val result = assertSuccess(tools().search("match", "."))

        assertEquals(listOf("notes.txt"), result.map { it.path })
    }

    // endregion

    // region createFile

    @Test
    fun createFile_writesContentAndCreatesParents() = runTest {
        val result = tools().createFile("a/b/c.txt", "hello")

        assertTrue(result is FileToolResult.Success)
        assertEquals("hello", File(tmp.root, "a/b/c.txt").readText())
    }

    @Test
    fun createFile_existingPath_isAlreadyExists() = runTest {
        write("a.txt", "old")

        val result = tools().createFile("a.txt", "new")

        assertFailure(result, FileToolErrorCode.ALREADY_EXISTS)
        assertEquals("old", File(tmp.root, "a.txt").readText())
    }

    // endregion

    // region createDirectory

    @Test
    fun createDirectory_createsDirectory() = runTest {
        val result = tools().createDirectory("nested/dir")

        assertTrue(result is FileToolResult.Success)
        assertTrue(File(tmp.root, "nested/dir").isDirectory)
    }

    @Test
    fun createDirectory_existingPath_isAlreadyExists() = runTest {
        File(tmp.root, "exists").mkdirs()

        val result = tools().createDirectory("exists")

        assertFailure(result, FileToolErrorCode.ALREADY_EXISTS)
    }

    // endregion

    // region writeFile

    @Test
    fun writeFile_overwritesExistingFile() = runTest {
        write("a.txt", "old")

        val result = tools().writeFile("a.txt", "new")

        assertTrue(result is FileToolResult.Success)
        assertEquals("new", File(tmp.root, "a.txt").readText())
    }

    @Test
    fun writeFile_createsNewFileAndParents() = runTest {
        val result = tools().writeFile("x/y/z.txt", "hi")

        assertTrue(result is FileToolResult.Success)
        assertEquals("hi", File(tmp.root, "x/y/z.txt").readText())
    }

    // endregion

    // region editFile

    @Test
    fun editFile_replacesSingleOccurrence() = runTest {
        write("a.txt", "alpha\nbeta\nalpha")

        val result = assertSuccess(tools().editFile("a.txt", "beta", "BETA"))

        assertEquals(1, result.replacements)
        assertEquals("alpha\nBETA\nalpha", File(tmp.root, "a.txt").readText())
    }

    @Test
    fun editFile_missingOldString_isConflict() = runTest {
        write("a.txt", "hello")

        val result = tools().editFile("a.txt", "nope", "x")

        assertFailure(result, FileToolErrorCode.CONFLICT)
        assertEquals("hello", File(tmp.root, "a.txt").readText())
    }

    @Test
    fun editFile_nonUniqueOldString_isConflict() = runTest {
        write("a.txt", "alpha alpha")

        val result = tools().editFile("a.txt", "alpha", "x")

        assertFailure(result, FileToolErrorCode.CONFLICT)
    }

    @Test
    fun editFile_replaceAll_replacesEveryOccurrence() = runTest {
        write("a.txt", "alpha beta alpha")

        val result = assertSuccess(tools().editFile("a.txt", "alpha", "x", replaceAll = true))

        assertEquals(2, result.replacements)
        assertEquals("x beta x", File(tmp.root, "a.txt").readText())
    }

    @Test
    fun editFile_missingFile_isNotFound() = runTest {
        val result = tools().editFile("missing.txt", "a", "b")

        assertFailure(result, FileToolErrorCode.NOT_FOUND)
    }

    @Test
    fun editFile_binaryFile_isNotText() = runTest {
        write("image.png", "fake")

        val result = tools().editFile("image.png", "fake", "real")

        assertFailure(result, FileToolErrorCode.NOT_TEXT)
    }

    // endregion

    // region move

    @Test
    fun move_renamesFile() = runTest {
        write("old.txt", "content")

        val result = tools().move("old.txt", "new.txt")

        assertTrue(result is FileToolResult.Success)
        assertFalse(File(tmp.root, "old.txt").exists())
        assertEquals("content", File(tmp.root, "new.txt").readText())
    }

    @Test
    fun move_existingDestination_isAlreadyExists() = runTest {
        write("source.txt", "source")
        write("target.txt", "target")

        val result = tools().move("source.txt", "target.txt")

        assertFailure(result, FileToolErrorCode.ALREADY_EXISTS)
        assertTrue(File(tmp.root, "source.txt").exists())
    }

    @Test
    fun move_workspaceRoot_isRejected() = runTest {
        val result = tools().move(".", "elsewhere")

        assertFailure(result, FileToolErrorCode.WORKSPACE_ROOT)
    }

    @Test
    fun move_outsideWorkspace_isRejected() = runTest {
        write("a.txt", "content")

        val result = tools().move("a.txt", "../a.txt")

        assertFailure(result, FileToolErrorCode.PATH_ESCAPE)
    }

    // endregion

    // region delete

    @Test
    fun delete_removesFile() = runTest {
        write("a.txt", "content")

        val result = tools().delete("a.txt")

        assertTrue(result is FileToolResult.Success)
        assertFalse(File(tmp.root, "a.txt").exists())
    }

    @Test
    fun delete_directoryWithoutRecursive_isRejected() = runTest {
        File(tmp.root, "dir").mkdirs()

        val result = tools().delete("dir")

        assertFailure(result, FileToolErrorCode.IO_ERROR)
        assertTrue(File(tmp.root, "dir").exists())
    }

    @Test
    fun delete_directoryWithRecursive_removesTree() = runTest {
        write("dir/nested/file.txt", "content")

        val result = tools().delete("dir", recursive = true)

        assertTrue(result is FileToolResult.Success)
        assertFalse(File(tmp.root, "dir").exists())
    }

    @Test
    fun delete_workspaceRoot_isRejected() = runTest {
        val result = tools().delete(".", recursive = true)

        assertFailure(result, FileToolErrorCode.WORKSPACE_ROOT)
    }

    @Test
    fun delete_missingPath_isNotFound() = runTest {
        val result = tools().delete("missing.txt")

        assertFailure(result, FileToolErrorCode.NOT_FOUND)
    }

    // endregion

    // region review fixes

    @Test
    fun search_tooLargeFile_isTooLarge() = runTest {
        write("big.log", "a".repeat(1_000_001))

        val result = tools().search("needle", ".")

        assertFailure(result, FileToolErrorCode.TOO_LARGE)
    }

    @Test
    fun list_skipsSymlinks() = runTest {
        write("real.txt", "content")
        try {
            Files.createSymbolicLink(File(tmp.root, "link.txt").toPath(), File(tmp.root, "real.txt").toPath())
        } catch (error: Exception) {
            assumeTrue("Symlinks unavailable on this platform: ${error.message}", false)
        }

        val result = assertSuccess(tools().list("."))

        assertEquals(listOf("real.txt"), result.map { it.name })
    }

    @Test
    fun createFile_throughDanglingSymlink_isRejected() = runTest {
        val danglingTarget = File(outside.root, "not-yet-created")
        try {
            Files.createSymbolicLink(File(tmp.root, "dangling").toPath(), danglingTarget.toPath())
        } catch (error: Exception) {
            assumeTrue("Symlinks unavailable on this platform: ${error.message}", false)
        }

        val result = tools().createFile("dangling/new.txt", "boom")

        assertFailure(result, FileToolErrorCode.PATH_ESCAPE)
        assertFalse(danglingTarget.exists())
    }

    // endregion
}
