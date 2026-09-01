package com.aeibi.design.data.projectfiles

/**
 * A structured, machine-readable result for every file-tool operation.
 * Operations return this instead of throwing, so an agent can branch on the
 * outcome without catching exceptions.
 */
sealed interface FileToolResult<out T> {
    data class Success<T>(val value: T) : FileToolResult<T>
    data class Failure(val error: FileToolError) : FileToolResult<Nothing>
}

data class FileToolError(val code: FileToolErrorCode, val message: String)

enum class FileToolErrorCode {
    /** The resolved path does not exist. */
    NOT_FOUND,

    /** The target is not a readable text file. */
    NOT_TEXT,

    /** The resolved path falls outside the workspace. */
    PATH_ESCAPE,

    /** The path (or another argument) is absolute, empty, or otherwise invalid. */
    INVALID_PATH,

    /** The target of a create/move operation already exists. */
    ALREADY_EXISTS,

    /** editFile: the old string is absent or (without replaceAll) not unique. */
    CONFLICT,

    /** The file exceeds the read/search size limit. */
    TOO_LARGE,

    /** A search/find result cap was exceeded. */
    TOO_MANY_RESULTS,

    /** An operation attempted to move/delete the workspace root. */
    WORKSPACE_ROOT,

    /** An underlying I/O failure. */
    IO_ERROR
}
