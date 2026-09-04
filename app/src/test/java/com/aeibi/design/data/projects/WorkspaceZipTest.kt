package com.aeibi.design.data.projects

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceZipTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun exportAndImport_roundTripsContent() {
        val source = temporaryFolder.newFolder("source")
        File(source, "index.html").writeText("<html/>")
        File(source, "assets/app.js").apply { parentFile.mkdirs() }.writeText("console.log(1)")
        File(source, "img/logo.png").apply { parentFile.mkdirs() }.writeText("png-data")
        val zip = File(temporaryFolder.root, "workspace.zip")

        WorkspaceZip.exportDirectory(source, zip)

        val target = temporaryFolder.newFolder("target")
        val count = WorkspaceZip.importArchive(zip, target)

        assertEquals(3, count)
        assertEquals("<html/>", File(target, "index.html").readText())
        assertEquals("console.log(1)", File(target, "assets/app.js").readText())
        assertEquals("png-data", File(target, "img/logo.png").readText())
    }

    @Test
    fun import_rejectsZipSlipEntries() {
        val zip = File(temporaryFolder.root, "evil.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("../escape.txt"))
            out.write("evil".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("safe.txt"))
            out.write("ok".toByteArray())
            out.closeEntry()
        }
        val target = temporaryFolder.newFolder("target")

        val count = WorkspaceZip.importArchive(zip, target)

        // 穿越条目被跳过，安全条目导入
        assertEquals(1, count)
        assertFalse("穿越文件不应写出", File(temporaryFolder.root, "escape.txt").exists())
        assertTrue(File(target, "safe.txt").exists())
    }

    @Test
    fun export_emptyDirectory_producesEmptyZip() {
        val source = temporaryFolder.newFolder("empty")
        val zip = File(temporaryFolder.root, "empty.zip")

        WorkspaceZip.exportDirectory(source, zip)

        assertTrue(zip.isFile)
        assertEquals(0, WorkspaceZip.importArchive(zip, temporaryFolder.newFolder("out")))
    }
}
