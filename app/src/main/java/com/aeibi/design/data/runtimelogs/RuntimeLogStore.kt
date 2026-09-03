package com.aeibi.design.data.runtimelogs

import javax.inject.Inject
import javax.inject.Singleton

/** 运行时日志条目（数据层模型——UI 与 agent 工具共用，与 webkit ConsoleMessage 解耦）。 */
data class RuntimeLogEntry(val level: String, val message: String, val source: String = "", val timestamp: Long = 0L)

/**
 * 预览 WebView 运行时日志存储（按项目分桶的内存环形缓冲）。
 *
 * UI 侧（ProjectWorkspaceViewModel）写入；agent 工具（RuntimeLogsTool）读取——
 * 连接"预览运行时反馈"与"agent 修复闭环"的数据层。
 */
@Singleton
class RuntimeLogStore @Inject constructor() {

    private val lock = Any()
    private val buffers = mutableMapOf<String, ArrayDeque<RuntimeLogEntry>>()

    fun record(projectId: String, entry: RuntimeLogEntry) {
        synchronized(lock) {
            val buffer = buffers.getOrPut(projectId) { ArrayDeque() }
            buffer.addLast(entry)
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
        }
    }

    fun clear(projectId: String) {
        synchronized(lock) {
            buffers.remove(projectId)
        }
    }

    fun snapshot(projectId: String): List<RuntimeLogEntry> = synchronized(lock) {
        buffers[projectId]?.toList().orEmpty()
    }

    private companion object {
        const val MAX_ENTRIES = 200
    }
}
