package com.aeibi.design.data.projectfiles

import com.aeibi.design.data.projects.WORKSPACE_DIR
import java.io.File

/** Produces a [ProjectFileTools] bound to a single project's workspace directory. */
class ProjectFileToolsFactory(private val projectsDir: File) {

    fun create(projectId: String): ProjectFileTools =
        ProjectFileTools(File(projectsDir, projectId).resolve(WORKSPACE_DIR))
}
