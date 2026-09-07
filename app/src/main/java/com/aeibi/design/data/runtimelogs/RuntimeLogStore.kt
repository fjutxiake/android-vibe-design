package com.aeibi.design.data.runtimelogs

import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 运行时日志条目（数据层模型——UI 与 agent 工具共用的单一来源）。 */
data class RuntimeLogEntry(val level: String, val message: String, val source: String = "", val timestamp: Long = 0L)

/**
 * 预览 WebView 运行时日志——**单一来源**：Console 展示与 agent 的
 * read_runtime_logs 都读这里（不维护两份可能不同步的数据）。
 *
 * 按项目分桶的 StateFlow 环形缓冲——UI 观察同一份数据，刷新（清空后 reload）
 * 后旧日志不残留。
 */
@Singleton
class RuntimeLogStore @Inject constructor() {

    private val lock = Any()
    private val buffers = mutableMapOf<String, MutableStateFlow<List<RuntimeLogEntry>>>()

    /** 观察某项目的当前日志（Console 面板展示源）。 */
    fun observe(projectId: String): Flow<List<RuntimeLogEntry>> = synchronized(lock) {
        buffers.getOrPut(projectId) { MutableStateFlow(emptyList()) }.asStateFlow()
    }

    fun record(projectId: String, entry: RuntimeLogEntry) {
        synchronized(lock) {
            val flow = buffers.getOrPut(projectId) { MutableStateFlow(emptyList()) }
            val buffer = ArrayDeque(flow.value)
            buffer.addLast(entry)
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
            flow.value = buffer.toList()
        }
    }

    fun clear(projectId: String) {
        synchronized(lock) {
            buffers.remove(projectId)?.value = emptyList()
        }
    }

    fun snapshot(projectId: String): List<RuntimeLogEntry> = synchronized(lock) {
        buffers[projectId]?.value.orEmpty()
    }

    private companion object {
        const val MAX_ENTRIES = 200
    }
}
