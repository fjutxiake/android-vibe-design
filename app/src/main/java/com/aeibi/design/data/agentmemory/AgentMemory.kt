package com.aeibi.design.data.agentmemory

import java.io.File

/**
 * agent 预设/记忆/技能 → prompt 注入文本。
 *
 * 隔离规则（对齐「工作区按项目隔离 → agent 按会话隔离」的项目结构哲学），
 * 四层按稳定性与权威排序注入：
 * - **会话预设**（preset，用户权威）：`.agent/<sessionId>/preset.md`——定义该 agent
 *   的身份/规则/工作方式。用户（或未来的会话创建 UI）提供，agent 不维护——
 *   因此**不注入维护引导**（预设是用户域，agent 不得越权改写）。
 * - **会话记忆**（memory，agent 权威）：`.agent/<sessionId>/memory.md`——运行中
 *   积累的约定，agent 用 write_file 维护（多会话 agent 并行零争写、互不可见注入）。
 * - **项目技能**（skills，项目资产）：`skills/<name>.md`——共享可复用指令段，
 *   按文件名排序注入所有会话。
 * - **路径引导**（总是存在）：agent 不知道自己的 sessionId，记忆文件路径必须由
 *   harness 告知，否则机制不可发现。
 */
class AgentMemory(private val workspace: File, private val sessionId: String) {

    /** 组装注入文本（预设 → 记忆 → 技能 → 引导）。 */
    fun readInjection(): String {
        val sections = mutableListOf<String>()
        readSection(PRESET_FILE_NAME, "[Session preset]")?.let(sections::add)
        readSection(MEMORY_FILE_NAME, "[Session memory]")?.let { memory ->
            sections += memory +
                "\n\n(Keep durable conventions for THIS session in the memory file — it is injected at the start of every turn of this session.)"
        }
        skillsDirectory().takeIf { it.isDirectory }?.listFiles()
            ?.filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.forEach { skillFile ->
                val content = skillFile.readText().trim()
                if (content.isNotEmpty()) {
                    sections += "[Skill: ${skillFile.nameWithoutExtension}]\n$content"
                }
            }
        // 引导行总是存在——agent 需要知道自己的记忆文件路径才能创建/维护它。
        sections += buildString {
            append("[Memory file]\n")
            append("Your per-session memory file is ${sessionRelativePath(MEMORY_FILE_NAME)} — ")
            append("durable conventions and decisions for this session belong there. ")
            append("Create it with write_file if missing; it is injected at the start of every turn of this session.")
        }

        val total = sections.fold(0) { acc, section -> acc + section.length }
        return if (total <= MAX_INJECTION_CHARS) {
            sections.joinToString("\n\n")
        } else {
            // 超限：保留预设/记忆段与靠前的技能，截断并标注（防 token 失控）。
            val builder = StringBuilder()
            for (section in sections) {
                if (builder.length + section.length + 2 > MAX_INJECTION_CHARS) break
                if (builder.isNotEmpty()) builder.append("\n\n")
                builder.append(section)
            }
            builder.append("\n\n(memory/skill injection truncated by size limit)")
            builder.toString()
        }
    }

    /** 读会话目录下某文件；不存在/空白返回 null。 */
    private fun readSection(fileName: String, header: String): String? = sessionFile(fileName).let { file ->
        file.takeIf { it.exists() }?.readText()?.trim()?.takeIf(String::isNotEmpty)?.let { content ->
            "$header\n$content"
        }
    }

    private fun sessionFile(fileName: String): File = File(workspace, sessionRelativePath(fileName))

    private fun sessionRelativePath(fileName: String): String = ".agent/$sessionId/$fileName"

    private fun skillsDirectory(): File = File(workspace, SKILLS_DIR_NAME)

    private companion object {
        const val PRESET_FILE_NAME = "preset.md"
        const val MEMORY_FILE_NAME = "memory.md"
        const val SKILLS_DIR_NAME = "skills"
        const val MAX_INJECTION_CHARS = 8000
    }
}
