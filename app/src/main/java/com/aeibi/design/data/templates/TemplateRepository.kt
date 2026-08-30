package com.aeibi.design.data.templates

import android.content.res.AssetManager
import android.util.Log
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class TemplateRepository(
    private val assets: AssetManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listTemplates(): List<Template> = withContext(ioDispatcher) {
        assets.list(TEMPLATES_ROOT).orEmpty().mapNotNull(::loadTemplate)
    }

    suspend fun getTemplate(id: String): Template? = withContext(ioDispatcher) {
        loadTemplate(id)
    }

    suspend fun readReadme(template: Template): String? = withContext(ioDispatcher) {
        template.readmeAssetPath?.let { path ->
            assets.open(path).bufferedReader().use { it.readText() }
        }
    }

    private fun loadTemplate(templateId: String): Template? {
        val templateRoot = "$TEMPLATES_ROOT/$templateId"
        val metadata = try {
            assets.open("$templateRoot/$TEMPLATE_JSON").bufferedReader().use { reader ->
                json.decodeFromString<TemplateMetadata>(reader.readText())
            }
        } catch (error: IOException) {
            Log.w(TAG, "Skipping template '$templateId': template.json could not be read", error)
            return null
        } catch (error: SerializationException) {
            Log.w(TAG, "Skipping template '$templateId': template.json could not be parsed", error)
            return null
        }

        return Template(
            id = templateId,
            category = metadata.category,
            name = metadata.name,
            description = metadata.description,
            coverAssetPath = "$templateRoot/${metadata.cover}",
            previewAssetPaths = metadata.previews.map { "$templateRoot/$it" },
            readmeAssetPath = if (README_MD in assets.list(templateRoot).orEmpty()) {
                "$templateRoot/$README_MD"
            } else {
                null
            },
            workspaceAssetPath = "$templateRoot/$WORKSPACE_ROOT"
        )
    }

    private companion object {
        const val TAG = "TemplateRepository"
        const val TEMPLATES_ROOT = "workspace-templates"
        const val TEMPLATE_JSON = "template.json"
        const val README_MD = "README.md"
        const val WORKSPACE_ROOT = "workspace"
    }
}

@Serializable
private data class TemplateMetadata(
    val category: String,
    val name: String,
    val description: String,
    val cover: String,
    val previews: List<String> = emptyList()
)
