package com.aeibi.design.data.runtimelogs

import javax.inject.Inject
import javax.inject.Singleton

/** 运行时日志条目（数据层模型——UI 与 agent 工具共用，与 webkit ConsoleMessage 解耦）。 */
data class RuntimeLogEntry(val level: String, val message: String, val source: String = "", val timestamp: Long = 0L)

/**
 * 预览 WebView 运行时日志存储（内存环形缓冲）。
 *
 * UI 侧（ProjectWorkspaceViewModel）写入；agent 工具（RuntimeLogsTool）读取——
 * 连接"预览运行时反馈"与"agent 修复闭环"的数据层。
 */
@Singleton
class RuntimeLogStore @Inject constructor() {

    private val lock = Any()
    private val buffer = ArrayDeque<RuntimeLogEntry>()
    private var snapshot: List<RuntimeLogEntry> = emptyList()

    fun record(entry: RuntimeLogEntry) {
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
            snapshot = buffer.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            snapshot = emptyList()
        }
    }

    fun snapshot(): List<RuntimeLogEntry> = snapshot

    private companion object {
        const val MAX_ENTRIES = 200
    }
}
