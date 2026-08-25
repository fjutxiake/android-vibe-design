package com.aeibi.design.data.projects

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IconCopyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 先吐出一部分字节再抛异常,模拟读到一半失败的输入流。 */
    private class HalfwayFailingStream(private val prefix: ByteArray) : InputStream() {
        private var served = false

        override fun read(): Int = throw IOException("读取中断")

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (served) throw IOException("读取中断")
            served = true
            val count = minOf(len, prefix.size)
            prefix.copyInto(b, off, 0, count)
            return count
        }
    }

    @Test
    fun copyIconAtomically_writesIconAndReturnsFilename() {
        val dir = tmp.newFolder()

        val name = copyIconAtomically(dir, "png") { ByteArrayInputStream("图标内容".toByteArray()) }

        assertEquals("icon.png", name)
        assertEquals("图标内容", File(dir, "icon.png").readText())
    }

    @Test
    fun copyIconAtomically_whenInputCannotBeOpened_returnsNull() {
        val dir = tmp.newFolder()

        assertNull(copyIconAtomically(dir, "png") { null })

        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test
    fun copyIconAtomically_whenCopyFailsMidway_keepsPreviousIconBytes() {
        val dir = tmp.newFolder()
        val previous = "原始图标内容".toByteArray()
        File(dir, "icon.png").writeBytes(previous)

        assertThrows(IOException::class.java) {
            copyIconAtomically(dir, "png") { HalfwayFailingStream("坏数".toByteArray()) }
        }

        assertArrayEquals(previous, File(dir, "icon.png").readBytes())
    }

    @Test
    fun copyIconAtomically_whenCopyFailsMidway_leavesNoTempFile() {
        val dir = tmp.newFolder()
        File(dir, "icon.png").writeBytes("原始图标内容".toByteArray())

        runCatching { copyIconAtomically(dir, "png") { HalfwayFailingStream("坏数".toByteArray()) } }

        assertEquals(listOf("icon.png"), dir.list()!!.sorted())
    }

    @Test
    fun copyIconAtomically_replacesPreviousIconOfSameExtension() {
        val dir = tmp.newFolder()
        File(dir, "icon.png").writeText("旧图标")

        val name = copyIconAtomically(dir, "png") { ByteArrayInputStream("新图标".toByteArray()) }

        assertEquals("icon.png", name)
        assertEquals("新图标", File(dir, "icon.png").readText())
        assertEquals(listOf("icon.png"), dir.list()!!.sorted())
    }
}
