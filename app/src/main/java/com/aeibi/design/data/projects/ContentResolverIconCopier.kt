package com.aeibi.design.data.projects

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.File

@Singleton
class ContentResolverIconCopier @Inject constructor(@ApplicationContext private val context: Context) : IconCopier {

    override fun copy(uri: String?, projectDir: File): String? {
        if (uri == null) return null
        return runCatching {
            val resolved = uri.toUri()
            val mime = context.contentResolver.getType(resolved)
            val ext = mime?.substringAfter("/")?.takeIf { it.isNotBlank() } ?: "png"
            copyIconAtomically(projectDir, ext) { context.contentResolver.openInputStream(resolved) }
        }.getOrNull()
    }
}
