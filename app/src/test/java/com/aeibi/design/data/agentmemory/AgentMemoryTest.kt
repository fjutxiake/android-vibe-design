package com.aeibi.design.data.agentmemory

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AgentMemoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun noMemoryStillGuidesWithSessionMemoryFilePath() {
        val workspace = temporaryFolder.newFolder()

        val injection = AgentMemory(workspace, "session-a").readInjection()

        assertTrue("Always carries a guidance section", injection.contains("[Memory file]"))
        assertTrue("Guidance names the per-session path", injection.contains(".agent/session-a/memory.md"))
    }

    @Test
    fun sessionMemoryIsInjectedForItsOwnSessionOnly() {
        val workspace = temporaryFolder.newFolder()
        val sessionDir = File(workspace, ".agent/session-a").apply { mkdirs() }
        File(sessionDir, "memory.md").writeText("This session uses 16px base font.")

        val ownInjection = AgentMemory(workspace, "session-a").readInjection()
        assertTrue("Own memory is injected", ownInjection.contains("16px base font"))
        assertTrue(ownInjection.startsWith("[Session memory]"))

        val otherInjection = AgentMemory(workspace, "session-b").readInjection()
        assertFalse("Other session's memory is not injected", otherInjection.contains("16px base font"))
    }

    @Test
    fun sharedSkillsAreInjectedForEverySessionInNameOrder() {
        val workspace = temporaryFolder.newFolder()
        val skills = File(workspace, "skills").apply { mkdirs() }
        File(skills, "b-write.md").writeText("Always write files in UTF-8.")
        File(skills, "a-style.md").writeText("Use CSS variables for all colors.")
        File(skills, "notes.txt").writeText("ignored non-md file")

        val injection = AgentMemory(workspace, "session-a").readInjection()

        val aIndex = injection.indexOf("[Skill: a-style]")
        val bIndex = injection.indexOf("[Skill: b-write]")
        assertTrue(aIndex in 0 until bIndex)
        assertTrue(injection.contains("CSS variables"))
        assertTrue(injection.contains("UTF-8"))
        assertFalse(injection.contains("ignored non-md file"))
    }

    @Test
    fun blankSessionMemoryFallsBackToGuidanceOnly() {
        val workspace = temporaryFolder.newFolder()
        val sessionDir = File(workspace, ".agent/session-a").apply { mkdirs() }
        File(sessionDir, "memory.md").writeText("   \n  ")

        val injection = AgentMemory(workspace, "session-a").readInjection()

        assertFalse("Blank memory is not injected", injection.startsWith("[Session memory]"))
        assertTrue(injection.contains("[Memory file]"))
    }

    @Test
    fun presetIsInjectedBeforeMemory() {
        val workspace = temporaryFolder.newFolder()
        val sessionDir = File(workspace, ".agent/session-a").apply { mkdirs() }
        File(sessionDir, "preset.md").writeText("You are a visual design specialist. Reply in Chinese.")
        File(sessionDir, "memory.md").writeText("User prefers 16px base font.")

        val injection = AgentMemory(workspace, "session-a").readInjection()

        assertTrue("Preset is injected", injection.startsWith("[Session preset]\nYou are a visual design specialist"))
        assertTrue(
            "Preset section precedes memory section",
            injection.indexOf("[Session preset]") < injection.indexOf("[Session memory]")
        )
        assertTrue(injection.contains("16px base font"))
    }

    @Test
    fun presetIsIsolatedPerSession() {
        val workspace = temporaryFolder.newFolder()
        val sessionDir = File(workspace, ".agent/session-a").apply { mkdirs() }
        File(sessionDir, "preset.md").writeText("Persona for session-a only.")

        val own = AgentMemory(workspace, "session-a").readInjection()
        assertTrue(own.contains("Persona for session-a only."))

        val other = AgentMemory(workspace, "session-b").readInjection()
        assertFalse("Other session's preset is not injected", other.contains("Persona for session-a only."))
    }

    @Test
    fun oversizedInjectionIsTruncatedWithMarker() {
        val workspace = temporaryFolder.newFolder()
        val sessionDir = File(workspace, ".agent/session-a").apply { mkdirs() }
        File(sessionDir, "memory.md").writeText("memory-" + "x".repeat(6_000))
        val skills = File(workspace, "skills").apply { mkdirs() }
        File(skills, "big.md").writeText("skill-" + "y".repeat(6_000))

        val injection = AgentMemory(workspace, "session-a").readInjection()

        assertTrue(injection.length <= 8_500)
        assertTrue(
            "Truncation is explicit",
            injection.endsWith("(memory/skill injection truncated by size limit)")
        )
        assertTrue(injection.startsWith("[Session memory]"))
    }
}
