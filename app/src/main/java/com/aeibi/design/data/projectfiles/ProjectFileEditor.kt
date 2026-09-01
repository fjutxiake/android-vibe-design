package com.aeibi.design.data.projectfiles

/** Mutating file operations, scoped to a single project workspace. */
interface ProjectFileEditor {
    suspend fun createFile(path: String, content: String): FileToolResult<Unit>

    suspend fun createDirectory(path: String): FileToolResult<Unit>

    suspend fun writeFile(path: String, content: String): FileToolResult<Unit>

    suspend fun editFile(
        path: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean = false
    ): FileToolResult<EditOutcome>

    suspend fun move(source: String, destination: String): FileToolResult<Unit>

    suspend fun delete(path: String, recursive: Boolean = false): FileToolResult<Unit>
}
