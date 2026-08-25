package com.aeibi.design.data.projects

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtomicWriteTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun writeAtomically_replacesExistingContent() {
        val dir = tmp.newFolder()
        val target = File(dir, "project.json").apply { writeText("旧内容") }

        target.writeAtomically { it.writeText("新内容") }

        assertEquals("新内容", target.readText())
    }

    @Test
    fun writeAtomically_createsTargetWhenMissing() {
        val dir = tmp.newFolder()
        val target = File(dir, "project.json")

        target.writeAtomically { it.writeText("首次写入") }

        assertEquals("首次写入", target.readText())
    }

    @Test
    fun writeAtomically_whenWriteFails_keepsPreviousContent() {
        val dir = tmp.newFolder()
        val target = File(dir, "project.json").apply { writeText("旧内容") }

        assertThrows(IOException::class.java) {
            target.writeAtomically {
                it.writeText("写了一半")
                throw IOException("磁盘写入失败")
            }
        }

        assertEquals("旧内容", target.readText())
    }

    @Test
    fun writeAtomically_whenWriteFails_removesTempFile() {
        val dir = tmp.newFolder()
        val target = File(dir, "project.json").apply { writeText("旧内容") }

        runCatching { target.writeAtomically { throw IOException("磁盘写入失败") } }

        assertEquals(listOf("project.json"), dir.list()!!.sorted())
    }

    @Test
    fun writeAtomically_leavesNoTempFileOnSuccess() {
        val dir = tmp.newFolder()
        val target = File(dir, "project.json")

        target.writeAtomically { it.writeText("内容") }

        assertEquals(listOf("project.json"), dir.list()!!.sorted())
    }

    @Test
    fun writeAtomically_writesToSameDirectorySoRenameStaysOnOneVolume() {
        val dir = tmp.newFolder()
        val target = File(dir, "project.json")
        var tempParent: File? = null

        target.writeAtomically {
            tempParent = it.parentFile
            it.writeText("内容")
        }

        assertTrue(tempParent!!.canonicalPath == dir.canonicalPath)
    }
}
