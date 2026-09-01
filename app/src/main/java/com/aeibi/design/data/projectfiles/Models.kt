package com.aeibi.design.data.projectfiles

/** A single directory entry produced by [ProjectFileReader.list]. */
data class FileEntry(val name: String, val path: String, val isDirectory: Boolean, val size: Long, val modifiedAt: Long)

/** The content of a text file read by [ProjectFileReader.read]. */
data class FileRead(val content: String, val truncated: Boolean)

/** A single regex match produced by [ProjectFileReader.search]. */
data class SearchMatch(val path: String, val line: Int, val lineText: String)

/** The outcome of [ProjectFileEditor.editFile]. */
data class EditOutcome(val replacements: Int)
