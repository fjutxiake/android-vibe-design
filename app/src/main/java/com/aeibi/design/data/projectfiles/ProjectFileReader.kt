package com.aeibi.design.data.projectfiles

/** Read-only file operations, scoped to a single project workspace. */
interface ProjectFileReader {
    suspend fun list(path: String = "."): FileToolResult<List<FileEntry>>

    suspend fun read(path: String, offset: Int? = null, limit: Int? = null): FileToolResult<FileRead>

    suspend fun find(pattern: String, path: String = "."): FileToolResult<List<String>>

    suspend fun search(regex: String, path: String = ".", maxResults: Int = 200): FileToolResult<List<SearchMatch>>
}
