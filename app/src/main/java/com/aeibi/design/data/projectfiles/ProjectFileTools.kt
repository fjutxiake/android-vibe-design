package com.aeibi.design.data.projectfiles

import com.aeibi.design.data.projectfiles.FileToolErrorCode.ALREADY_EXISTS
import com.aeibi.design.data.projectfiles.FileToolErrorCode.CONFLICT
import com.aeibi.design.data.projectfiles.FileToolErrorCode.INVALID_PATH
import com.aeibi.design.data.projectfiles.FileToolErrorCode.IO_ERROR
import com.aeibi.design.data.projectfiles.FileToolErrorCode.NOT_FOUND
import com.aeibi.design.data.projectfiles.FileToolErrorCode.NOT_TEXT
import com.aeibi.design.data.projectfiles.FileToolErrorCode.TOO_LARGE
import com.aeibi.design.data.projectfiles.FileToolErrorCode.TOO_MANY_RESULTS
import com.aeibi.design.data.projectfiles.FileToolErrorCode.WORKSPACE_ROOT
import java.io.File
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.regex.PatternSyntaxException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A sandboxed file tool bound to a single project workspace root at construction.
 *
 * All operations are constrained to [workspaceRoot] (canonicalized once, so the instance
 * cannot see outside its own sandbox), run I/O on [ioDispatcher], and return structured
 * [FileToolResult]s instead of throwing. Mutating operations are serialized by a per-instance
 * [Mutex] to keep read-modify-write operations (like [editFile]) free of races.
 */
class ProjectFileTools(workspaceRoot: File, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) :
    ProjectFileReader,
    ProjectFileEditor {

    private val workspacePath = WorkspacePath(workspaceRoot)
    private val writeMutex = Mutex()

    override suspend fun list(path: String): FileToolResult<List<FileEntry>> = withContext(ioDispatcher) {
        resolveAndRun(path) { target ->
            if (!target.exists()) return@resolveAndRun failure(NOT_FOUND, "Path does not exist: $path")
            if (!target.isDirectory) return@resolveAndRun failure(NOT_FOUND, "Not a directory: $path")
            val entries = target.listFiles().orEmpty()
                .filter { !Files.isSymbolicLink(it.toPath()) }
                .map { child ->
                    FileEntry(
                        name = child.name,
                        path = workspacePath.relativePath(child),
                        isDirectory = child.isDirectory,
                        size = if (child.isDirectory) 0 else child.length(),
                        modifiedAt = child.lastModified()
                    )
                }
                .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
            FileToolResult.Success(entries)
        }
    }

    override suspend fun read(path: String, offset: Int?, limit: Int?): FileToolResult<FileRead> =
        withContext(ioDispatcher) {
            resolveAndRun(path) { target ->
                if (!target.exists()) return@resolveAndRun failure(NOT_FOUND, "File does not exist: $path")
                when (val text = readTextChecked(target)) {
                    is TextFile.Content -> {
                        val start = (offset ?: 1).coerceAtLeast(1)
                        val maxLines = limit?.coerceAtLeast(0) ?: MAX_READ_LINES
                        val window = text.value.lineSequence()
                            .drop(start - 1)
                            .take(maxLines + 1)
                            .toList()
                        FileToolResult.Success(
                            FileRead(
                                content = window.take(maxLines).joinToString("\n"),
                                truncated = window.size > maxLines
                            )
                        )
                    }
                    TextFile.NotText -> failure(NOT_TEXT, "Not a text file: $path")
                    TextFile.TooLarge -> failure(TOO_LARGE, "File is too large: $path")
                }
            }
        }

    override suspend fun find(pattern: String, path: String): FileToolResult<List<String>> = withContext(ioDispatcher) {
        resolveAndRun(path) { target ->
            if (!target.exists()) return@resolveAndRun failure(NOT_FOUND, "Path does not exist: $path")
            if (!target.isDirectory) return@resolveAndRun failure(NOT_FOUND, "Not a directory: $path")
            val matcher = compileGlob(pattern)
            val results = mutableListOf<String>()
            val withinLimit = findRecursive(target, target, matcher, results, 0)
            if (withinLimit) {
                FileToolResult.Success(results)
            } else {
                failure(TOO_MANY_RESULTS, "Too many matches for pattern '$pattern'; narrow the search")
            }
        }
    }

    override suspend fun search(regex: String, path: String, maxResults: Int): FileToolResult<List<SearchMatch>> =
        withContext(ioDispatcher) {
            resolveAndRun(path) { target ->
                if (!target.exists()) return@resolveAndRun failure(NOT_FOUND, "Path does not exist: $path")
                val regexObject = try {
                    Regex(regex)
                } catch (error: PatternSyntaxException) {
                    return@resolveAndRun failure(INVALID_PATH, "Invalid regex: $regex")
                }
                val matches = mutableListOf<SearchMatch>()
                val outcome = when {
                    target.isDirectory -> searchRecursive(target, regexObject, maxResults, matches, 0)
                    target.isFile -> searchFile(target, regexObject, maxResults, matches)
                    else -> return@resolveAndRun failure(NOT_FOUND, "Not a file or directory: $path")
                }
                when (outcome) {
                    SearchOutcome.COMPLETE -> FileToolResult.Success(matches)
                    SearchOutcome.TOO_MANY -> failure(
                        TOO_MANY_RESULTS,
                        "More than $maxResults matches; narrow the search"
                    )
                    SearchOutcome.TOO_LARGE -> failure(
                        TOO_LARGE,
                        "A file exceeds the search size limit; narrow the search"
                    )
                }
            }
        }

    override suspend fun createFile(path: String, content: String): FileToolResult<Unit> = withContext(ioDispatcher) {
        writeMutex.withLock {
            resolveAndRun(path) { target ->
                if (target.exists()) return@resolveAndRun failure(ALREADY_EXISTS, "Path already exists: $path")
                writeTextAtomic(target, content)
            }
        }
    }

    override suspend fun createDirectory(path: String): FileToolResult<Unit> = withContext(ioDispatcher) {
        writeMutex.withLock {
            resolveAndRun(path) { target ->
                if (target.exists()) return@resolveAndRun failure(ALREADY_EXISTS, "Path already exists: $path")
                if (target.mkdirs()) {
                    FileToolResult.Success(Unit)
                } else {
                    failure(IO_ERROR, "Could not create directory: $path")
                }
            }
        }
    }

    override suspend fun writeFile(path: String, content: String): FileToolResult<Unit> = withContext(ioDispatcher) {
        writeMutex.withLock {
            resolveAndRun(path) { target ->
                if (target.isDirectory) return@resolveAndRun failure(IO_ERROR, "Cannot write over a directory: $path")
                writeTextAtomic(target, content)
            }
        }
    }

    override suspend fun editFile(
        path: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean
    ): FileToolResult<EditOutcome> = withContext(ioDispatcher) {
        writeMutex.withLock {
            resolveAndRun(path) { target ->
                if (!target.exists()) return@resolveAndRun failure(NOT_FOUND, "File does not exist: $path")
                if (oldString.isEmpty()) return@resolveAndRun failure(INVALID_PATH, "oldString must not be empty")
                val content = when (val text = readTextChecked(target)) {
                    is TextFile.Content -> text.value
                    TextFile.NotText -> return@resolveAndRun failure(NOT_TEXT, "Not a text file: $path")
                    TextFile.TooLarge -> return@resolveAndRun failure(TOO_LARGE, "File is too large: $path")
                }
                val occurrences = countOccurrences(content, oldString)
                if (occurrences == 0) return@resolveAndRun failure(CONFLICT, "oldString not found in $path")
                if (!replaceAll && occurrences > 1) {
                    return@resolveAndRun failure(CONFLICT, "oldString is not unique in $path; use replaceAll")
                }
                val updated = if (replaceAll) {
                    content.replace(oldString, newString)
                } else {
                    content.replaceFirst(oldString, newString)
                }
                when (val written = writeTextAtomic(target, updated)) {
                    is FileToolResult.Success -> FileToolResult.Success(EditOutcome(if (replaceAll) occurrences else 1))
                    is FileToolResult.Failure -> written
                }
            }
        }
    }

    override suspend fun move(source: String, destination: String): FileToolResult<Unit> = withContext(ioDispatcher) {
        writeMutex.withLock {
            try {
                val sourceFile = workspacePath.resolve(source)
                val destinationFile = workspacePath.resolve(destination)
                if (!sourceFile.exists()) return@withLock failure(NOT_FOUND, "Source does not exist: $source")
                if (workspacePath.isRoot(sourceFile)) {
                    return@withLock failure(WORKSPACE_ROOT, "Cannot move the workspace root")
                }
                if (destinationFile.exists()) {
                    return@withLock failure(ALREADY_EXISTS, "Destination already exists: $destination")
                }
                Files.move(sourceFile.toPath(), destinationFile.toPath())
                FileToolResult.Success(Unit)
            } catch (error: WorkspacePathException) {
                FileToolResult.Failure(FileToolError(error.code, error.message ?: "Invalid path"))
            } catch (error: IOException) {
                failure(IO_ERROR, error.message ?: "Failed to move $source")
            }
        }
    }

    override suspend fun delete(path: String, recursive: Boolean): FileToolResult<Unit> = withContext(ioDispatcher) {
        writeMutex.withLock {
            resolveAndRun(path) { target ->
                if (!target.exists()) return@resolveAndRun failure(NOT_FOUND, "Path does not exist: $path")
                if (workspacePath.isRoot(target)) {
                    return@resolveAndRun failure(WORKSPACE_ROOT, "Cannot delete the workspace root")
                }
                if (target.isDirectory && !recursive) {
                    return@resolveAndRun failure(
                        IO_ERROR,
                        "Refusing to delete a directory without recursive=true: $path"
                    )
                }
                val deleted = if (target.isDirectory) target.deleteRecursively() else target.delete()
                if (!deleted && target.exists()) return@resolveAndRun failure(IO_ERROR, "Failed to delete: $path")
                FileToolResult.Success(Unit)
            }
        }
    }

    private inline fun <T> resolveAndRun(path: String, block: (File) -> FileToolResult<T>): FileToolResult<T> = try {
        block(workspacePath.resolve(path))
    } catch (error: WorkspacePathException) {
        FileToolResult.Failure(FileToolError(error.code, error.message ?: "Invalid path"))
    } catch (error: IOException) {
        failure(IO_ERROR, error.message ?: "I/O error")
    }

    private fun failure(code: FileToolErrorCode, message: String): FileToolResult<Nothing> =
        FileToolResult.Failure(FileToolError(code, message))

    private fun writeTextAtomic(file: File, content: String): FileToolResult<Unit> {
        val parent = file.parentFile ?: return failure(IO_ERROR, "Cannot resolve parent directory: ${file.path}")
        if (!parent.isDirectory && !parent.mkdirs()) {
            return failure(IO_ERROR, "Cannot create parent directory: ${parent.path}")
        }
        val target = file.toPath()
        val temp = Files.createTempFile(parent.toPath(), "tmp", ".tmp")
        try {
            Files.write(temp, content.encodeToByteArray())
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (error: AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (error: Exception) {
            Files.deleteIfExists(temp)
            throw error
        }
        return FileToolResult.Success(Unit)
    }

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var index = text.indexOf(needle)
        while (index >= 0) {
            count++
            index = text.indexOf(needle, index + needle.length)
        }
        return count
    }

    private fun compileGlob(glob: String): Regex {
        val builder = StringBuilder("^")
        var index = 0
        while (index < glob.length) {
            when (val char = glob[index]) {
                '*' -> {
                    if (index + 1 < glob.length && glob[index + 1] == '*') {
                        index++
                        if (index + 1 < glob.length && glob[index + 1] == '/') {
                            index++
                            builder.append("(?:[^/]*/)*")
                        } else {
                            builder.append(".*")
                        }
                    } else {
                        builder.append("[^/]*")
                    }
                }
                '?' -> builder.append("[^/]")
                else -> builder.append(Regex.escape(char.toString()))
            }
            index++
        }
        builder.append('$')
        return Regex(builder.toString())
    }

    private fun findRecursive(
        root: File,
        dir: File,
        matcher: Regex,
        results: MutableList<String>,
        depth: Int
    ): Boolean {
        val children = dir.listFiles() ?: return true
        for (child in children.sortedBy { it.name }) {
            if (Files.isSymbolicLink(child.toPath())) continue
            when {
                child.isDirectory -> {
                    if (depth >= MAX_DEPTH) continue
                    if (!findRecursive(root, child, matcher, results, depth + 1)) return false
                }
                child.isFile -> {
                    val relative = root.toPath().relativize(child.toPath())
                        .toString().replace(File.separatorChar, '/')
                    if (matcher.matches(relative)) {
                        results += workspacePath.relativePath(child)
                        if (results.size > MAX_SEARCH_RESULTS) return false
                    }
                }
            }
        }
        return true
    }

    private fun searchRecursive(
        dir: File,
        regex: Regex,
        maxResults: Int,
        matches: MutableList<SearchMatch>,
        depth: Int
    ): SearchOutcome {
        val children = dir.listFiles() ?: return SearchOutcome.COMPLETE
        for (child in children.sortedBy { it.name }) {
            if (Files.isSymbolicLink(child.toPath())) continue
            when {
                child.isDirectory -> {
                    if (depth >= MAX_DEPTH) continue
                    val outcome = searchRecursive(child, regex, maxResults, matches, depth + 1)
                    if (outcome != SearchOutcome.COMPLETE) return outcome
                }
                child.isFile -> {
                    val outcome = searchFile(child, regex, maxResults, matches)
                    if (outcome != SearchOutcome.COMPLETE) return outcome
                }
            }
        }
        return SearchOutcome.COMPLETE
    }

    private fun searchFile(
        file: File,
        regex: Regex,
        maxResults: Int,
        matches: MutableList<SearchMatch>
    ): SearchOutcome {
        val text = when (val content = readTextChecked(file)) {
            is TextFile.Content -> content.value
            TextFile.NotText -> return SearchOutcome.COMPLETE
            TextFile.TooLarge -> return SearchOutcome.TOO_LARGE
        }
        for ((lineIndex, line) in text.lineSequence().withIndex()) {
            if (regex.containsMatchIn(line)) {
                matches += SearchMatch(workspacePath.relativePath(file), lineIndex + 1, line)
                if (matches.size > maxResults) return SearchOutcome.TOO_MANY
            }
        }
        return SearchOutcome.COMPLETE
    }

    private fun readTextChecked(file: File): TextFile {
        if (!file.isFile) return TextFile.NotText
        if (file.length() > MAX_FILE_BYTES) return TextFile.TooLarge
        val extension = file.extension.lowercase()
        if (extension.isNotEmpty() && extension !in TEXT_EXTENSIONS) return TextFile.NotText
        val bytes = file.readBytes()
        if (bytes.indexOf(0.toByte()) >= 0) return TextFile.NotText
        return try {
            TextFile.Content(bytes.decodeToString(throwOnInvalidSequence = true))
        } catch (error: CharacterCodingException) {
            TextFile.NotText
        }
    }

    private companion object {
        const val MAX_READ_LINES = 2000
        const val MAX_FILE_BYTES = 1_000_000L
        const val MAX_SEARCH_RESULTS = 10_000
        const val MAX_DEPTH = 64

        val TEXT_EXTENSIONS = setOf(
            "html", "htm", "css", "js", "mjs", "cjs", "jsx", "ts", "tsx",
            "json", "jsonc", "md", "markdown", "txt", "text", "xml", "svg",
            "yaml", "yml", "toml", "ini", "conf", "cfg", "properties", "prop",
            "csv", "tsv", "log", "sh", "bash", "bat", "cmd", "ps1",
            "py", "rb", "go", "rs", "c", "h", "cc", "cpp", "hpp", "mm", "swift",
            "kt", "kts", "java", "gradle", "sql", "vue", "svelte", "scss", "sass", "less",
            "gitignore", "gitattributes", "gitmodules", "editorconfig", "dockerignore", "lock",
            "map", "env"
        )
    }
}

private enum class SearchOutcome {
    COMPLETE,
    TOO_MANY,
    TOO_LARGE
}

private sealed interface TextFile {
    data class Content(val value: String) : TextFile
    object NotText : TextFile
    object TooLarge : TextFile
}
