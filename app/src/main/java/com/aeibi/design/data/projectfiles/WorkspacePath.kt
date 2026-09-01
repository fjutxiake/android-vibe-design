package com.aeibi.design.data.projectfiles

import com.aeibi.design.data.projectfiles.FileToolErrorCode.INVALID_PATH
import com.aeibi.design.data.projectfiles.FileToolErrorCode.PATH_ESCAPE
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves raw, caller-supplied paths against a fixed workspace root and enforces that
 * every result stays inside it. Symbolic links are resolved (via [Files]' real-path
 * handling, which unlike [File.getCanonicalFile] also resolves links on Windows), so a
 * link cannot be used to escape the sandbox. Containment is checked against a stable real
 * root computed once at construction.
 */
internal class WorkspacePath(root: File) {

    private val rootPath: Path = root.absoluteFile.toPath().normalize()
    private val realRoot: Path = realPath(rootPath)

    /**
     * Resolves [rawPath] to a real (symlink-free) [File] inside the workspace.
     *
     * @throws WorkspacePathException with a typed [FileToolErrorCode] when the path is
     *   blank, absolute, or escapes the sandbox.
     */
    fun resolve(rawPath: String): File {
        if (rawPath.isBlank()) throw WorkspacePathException(INVALID_PATH, "Path must not be blank")
        if (isAbsolute(rawPath)) throw WorkspacePathException(INVALID_PATH, "Absolute paths are not allowed: $rawPath")

        val target = rootPath.resolve(rawPath).normalize()
        val realTarget = realPath(target)
        if (!realTarget.startsWith(realRoot)) {
            throw WorkspacePathException(PATH_ESCAPE, "Path escapes the workspace: $rawPath")
        }
        if (containsUnresolvedSymlink(target)) {
            throw WorkspacePathException(PATH_ESCAPE, "Path contains an unresolvable symlink: $rawPath")
        }
        return realTarget.toFile()
    }

    fun isRoot(file: File): Boolean = file.toPath() == realRoot

    /** Returns [file]'s path relative to the workspace root, using forward slashes. */
    fun relativePath(file: File): String =
        realRoot.relativize(file.toPath()).toString().replace(File.separatorChar, '/')

    private fun containsUnresolvedSymlink(target: Path): Boolean {
        var ancestor: Path? = target
        while (ancestor != null && !Files.exists(ancestor)) {
            ancestor = ancestor.parent
        }
        val base = ancestor ?: return false
        var cursor: Path = base
        for (component in base.relativize(target)) {
            cursor = cursor.resolve(component)
            if (Files.isSymbolicLink(cursor)) return true
        }
        return false
    }

    private fun isAbsolute(path: String): Boolean = File(path).isAbsolute ||
        path.startsWith('/') ||
        path.startsWith('\\') ||
        Regex("^[A-Za-z]:").containsMatchIn(path)
}

/**
 * Resolves the real (symlink-free) path of [path], tolerating a non-existent final segment:
 * the deepest existing ancestor is resolved with [Path.toRealPath], then the remaining
 * suffix is re-appended. Falls back to a normalized absolute path if the real path cannot
 * be resolved.
 */
private fun realPath(path: Path): Path {
    var ancestor: Path? = path
    while (ancestor != null && !Files.exists(ancestor)) {
        ancestor = ancestor.parent
    }
    if (ancestor == null) return path.toAbsolutePath().normalize()
    return try {
        ancestor.toRealPath().resolve(ancestor.relativize(path))
    } catch (error: IOException) {
        path.toAbsolutePath().normalize()
    }
}

internal class WorkspacePathException(val code: FileToolErrorCode, message: String) : Exception(message)
