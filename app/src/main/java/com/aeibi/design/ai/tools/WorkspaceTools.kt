package com.aeibi.design.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.stream.Collectors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@LLMDescription("Tools for reading and updating files in the current project workspace.")
class WorkspaceTools(rootDirectory: File, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : ToolSet {
    private val root = rootDirectory.toPath().toAbsolutePath().normalize()

    @Tool(customName = "list_files")
    @LLMDescription("List all files in the workspace recursively using relative paths.")
    suspend fun listFiles(): String = withContext(ioDispatcher) {
        if (!Files.exists(root)) return@withContext "Workspace is empty"
        val files = Files.walk(root).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .map(root::relativize)
                .map(Path::toString)
                .map { it.replace(File.separatorChar, '/') }
                .sorted()
                .collect(Collectors.toList())
        }
        files.joinToString("\n").ifEmpty { "Workspace is empty" }
    }

    @Tool(customName = "read_file")
    @LLMDescription("Read a UTF-8 text file from a relative workspace path.")
    suspend fun readFile(@LLMDescription("Relative path of the file to read.") path: String): String =
        withContext(ioDispatcher) {
            String(Files.readAllBytes(resolve(path)), StandardCharsets.UTF_8)
        }

    @Tool(customName = "write_file")
    @LLMDescription("Create or replace a UTF-8 text file at a relative workspace path.")
    suspend fun writeFile(
        @LLMDescription("Relative path of the file to write.") path: String,
        @LLMDescription("Complete UTF-8 text content for the file.") content: String
    ): String = withContext(ioDispatcher) {
        val target = resolve(path)
        writeAtomically(target, content)
        "Updated ${relativeName(target)}"
    }

    @Tool(customName = "replace_text")
    @LLMDescription("Replace text that occurs exactly once in a UTF-8 workspace file.")
    suspend fun replaceText(
        @LLMDescription("Relative path of the file to update.") path: String,
        @LLMDescription("Exact text that must occur once.") oldText: String,
        @LLMDescription("Replacement text.") newText: String
    ): String = withContext(ioDispatcher) {
        require(oldText.isNotEmpty()) { "Text to replace must not be empty" }
        val target = resolve(path)
        val content = String(Files.readAllBytes(target), StandardCharsets.UTF_8)
        val firstMatch = content.indexOf(oldText)
        val occurrences = when {
            firstMatch < 0 -> 0
            content.indexOf(oldText, firstMatch + 1) >= 0 -> 2
            else -> 1
        }
        require(occurrences == 1) { "Expected exactly one match but found $occurrences" }
        writeAtomically(target, content.replace(oldText, newText))
        "Updated ${relativeName(target)}"
    }

    private fun resolve(path: String): Path {
        val requested = Paths.get(path)
        require(!requested.isAbsolute) { "Path must be relative to the workspace" }
        return root.resolve(requested).normalize().also {
            require(it.startsWith(root)) { "Path escapes workspace" }
        }
    }

    private fun writeAtomically(target: Path, content: String) {
        val parent = target.parent ?: root
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".agent-", ".tmp")
        try {
            Files.write(temporary, content.toByteArray(StandardCharsets.UTF_8))
            try {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun relativeName(path: Path): String = root.relativize(path)
        .toString()
        .replace(File.separatorChar, '/')
}
