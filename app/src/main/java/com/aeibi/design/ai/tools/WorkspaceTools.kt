package com.aeibi.design.ai.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.aeibi.design.data.projectfiles.FileEntry
import com.aeibi.design.data.projectfiles.FileRead
import com.aeibi.design.data.projectfiles.FileToolError
import com.aeibi.design.data.projectfiles.FileToolResult
import com.aeibi.design.data.projectfiles.ProjectFileTools
import com.aeibi.design.data.projectfiles.SearchMatch

@LLMDescription("Tools for reading and updating files in the current project workspace.")
class WorkspaceTools(private val projectFiles: ProjectFileTools) : ToolSet {

    @Tool(customName = "list_files")
    @LLMDescription("List all files in the workspace recursively using relative paths.")
    suspend fun listFiles(): String = projectFiles.find("**/*").toToolOutput { files ->
        files.joinToString("\n").ifEmpty { "Workspace is empty" }
    }

    @Tool(customName = "list_directory")
    @LLMDescription("List the direct contents of a workspace directory. Directories end with '/'.")
    suspend fun listDirectory(@LLMDescription("Relative directory path, such as '.' or 'src'.") path: String): String =
        projectFiles.list(path).toToolOutput(::formatDirectory)

    @Tool(customName = "find_files")
    @LLMDescription("Find workspace files recursively with a glob pattern such as '**/*.html'.")
    suspend fun findFiles(
        @LLMDescription("Glob pattern to match.") pattern: String,
        @LLMDescription("Relative directory path to search.") path: String
    ): String = projectFiles.find(pattern, path).toToolOutput { files -> files.joinToString("\n") }

    @Tool(customName = "read_file")
    @LLMDescription("Read a UTF-8 text file from a relative workspace path. Output may be truncated.")
    suspend fun readFile(@LLMDescription("Relative path of the file to read.") path: String): String =
        projectFiles.read(path).toToolOutput(::formatRead)

    @Tool(customName = "read_file_range")
    @LLMDescription("Read a line range from a UTF-8 workspace file. Lines are 1-based.")
    suspend fun readFileRange(
        @LLMDescription("Relative path of the file to read.") path: String,
        @LLMDescription("First line to read, starting at 1.") offset: Int,
        @LLMDescription("Maximum number of lines to return.") limit: Int
    ): String = projectFiles.read(path, offset, limit).toToolOutput(::formatRead)

    @Tool(customName = "search_text")
    @LLMDescription("Search UTF-8 workspace files using a regular expression.")
    suspend fun searchText(
        @LLMDescription("Regular expression to search for.") regex: String,
        @LLMDescription("Relative file or directory path to search.") path: String
    ): String = projectFiles.search(regex, path).toToolOutput(::formatSearch)

    @Tool(customName = "create_file")
    @LLMDescription("Create a new UTF-8 file. Fails if the path already exists.")
    suspend fun createFile(
        @LLMDescription("Relative path of the file to create.") path: String,
        @LLMDescription("Complete UTF-8 text content for the file.") content: String
    ): String = projectFiles.createFile(path, content).toToolOutput { "Created $path" }

    @Tool(customName = "create_directory")
    @LLMDescription("Create a new workspace directory. Fails if the path already exists.")
    suspend fun createDirectory(@LLMDescription("Relative path of the directory to create.") path: String): String =
        projectFiles.createDirectory(path).toToolOutput { "Created directory $path" }

    @Tool(customName = "write_file")
    @LLMDescription("Create or replace a UTF-8 text file at a relative workspace path.")
    suspend fun writeFile(
        @LLMDescription("Relative path of the file to write.") path: String,
        @LLMDescription("Complete UTF-8 text content for the file.") content: String
    ): String = projectFiles.writeFile(path, content).toToolOutput { "Updated $path" }

    @Tool(customName = "replace_text")
    @LLMDescription("Replace text that occurs exactly once in a UTF-8 workspace file.")
    suspend fun replaceText(
        @LLMDescription("Relative path of the file to update.") path: String,
        @LLMDescription("Exact text that must occur once.") oldText: String,
        @LLMDescription("Replacement text.") newText: String
    ): String = projectFiles.editFile(path, oldText, newText).toToolOutput { "Updated $path" }

    @Tool(customName = "move_path")
    @LLMDescription("Move or rename a workspace file or directory. Fails if the destination exists.")
    suspend fun movePath(
        @LLMDescription("Relative source path.") source: String,
        @LLMDescription("Relative destination path.") destination: String
    ): String = projectFiles.move(source, destination).toToolOutput { "Moved $source to $destination" }

    @Tool(customName = "delete_path")
    @LLMDescription("Delete a workspace file or directory. Set recursive=true only when deleting a directory tree.")
    suspend fun deletePath(
        @LLMDescription("Relative path to delete.") path: String,
        @LLMDescription("Whether to delete a directory and all of its contents.") recursive: Boolean
    ): String = projectFiles.delete(path, recursive).toToolOutput { "Deleted $path" }

    private fun formatDirectory(entries: List<FileEntry>): String = entries.joinToString("\n") { entry ->
        if (entry.isDirectory) "${entry.path}/" else entry.path
    }.ifEmpty { "Directory is empty" }

    private fun formatSearch(matches: List<SearchMatch>): String = matches.joinToString("\n") { match ->
        "${match.path}:${match.line}: ${match.lineText}"
    }.ifEmpty { "No matches" }

    private fun formatRead(read: FileRead): String = buildString {
        append(read.content)
        if (read.truncated) {
            if (read.content.isNotEmpty()) append('\n')
            append("[Output truncated; use a later line offset to continue reading.]")
        }
    }
}

private inline fun <T> FileToolResult<T>.toToolOutput(onSuccess: (T) -> String): String = when (this) {
    is FileToolResult.Success -> onSuccess(value)
    is FileToolResult.Failure -> error.toToolOutput()
}

private fun FileToolError.toToolOutput(): String = "ERROR [${code.name}]: $message"
