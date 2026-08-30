package com.aeibi.design.data.templates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateRepositoryTest {

    private val targetContext: Context = ApplicationProvider.getApplicationContext()
    private val repository = TemplateRepository(targetContext.assets)

    @Test
    fun listTemplates_returnsAllBuiltInTemplatesWithAssetPaths() = runBlocking {
        val templates = repository.listTemplates()
        val templatesById = templates.associateBy(Template::id)
        val expectedIds = setOf(
            "focus-planner",
            "mini-store",
            "tabbed-workspace",
            "travel-journal"
        )

        assertEquals(expectedIds, templatesById.keys)
        templates.forEach { template ->
            val root = "workspace-templates/${template.id}"
            assertEquals("$root/previews/cover.webp", template.coverAssetPath)
            assertEquals("$root/workspace", template.workspaceAssetPath)
            assertEquals("$root/README.md", template.readmeAssetPath)
        }
        assertEquals(
            listOf(
                "workspace-templates/tabbed-workspace/previews/activity.webp",
                "workspace-templates/tabbed-workspace/previews/profile.webp"
            ),
            templatesById.getValue("tabbed-workspace").previewAssetPaths
        )
        assertTrue(
            templates
                .filterNot { it.id == "tabbed-workspace" }
                .all { it.previewAssetPaths.isEmpty() }
        )
    }

    @Test
    fun getTemplate_returnsMatchingTemplateOrNull() = runBlocking {
        assertEquals("mini-store", repository.getTemplate("mini-store")?.id)
        assertNull(repository.getTemplate("missing-template"))
    }

    @Test
    fun readReadme_readsEveryAvailableReadme() = runBlocking {
        repository.listTemplates().forEach { template ->
            assertNotNull(template.readmeAssetPath)
            assertTrue(repository.readReadme(template).orEmpty().isNotBlank())
        }
    }

    @Test
    fun listTemplates_skipsInvalidMetadataWithoutCheckingWorkspace() = runBlocking {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val templates = TemplateRepository(testAssets).listTemplates()

        assertEquals(listOf("valid-template"), templates.map(Template::id))
        assertEquals(
            "workspace-templates/valid-template/workspace",
            templates.single().workspaceAssetPath
        )
    }
}
