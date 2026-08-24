package com.aeibi.design.data.projects

import java.io.File
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ContentResolverIconCopier @Inject constructor(
    @ApplicationContext private val context: Context,
) : IconCopier {

    override fun copy(uri: String?, projectDir: File): String? {
        if (uri == null) return null
        return runCatching {
            val resolved = Uri.parse(uri)
            val mime = context.contentResolver.getType(resolved)
            val ext = mime?.substringAfter("/")?.takeIf { it.isNotBlank() } ?: "png"
            val target = File(projectDir, "icon.$ext")
            context.contentResolver.openInputStream(resolved)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.name
        }.getOrNull()
    }
}
