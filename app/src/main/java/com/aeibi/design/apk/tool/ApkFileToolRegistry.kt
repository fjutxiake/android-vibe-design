package com.aeibi.design.apk.tool

/**
 * 工具注册表：按名称索引已注册的 [ApkFileTool]。
 *
 * Operation（复合修改）通过注册表调用原子工具完成文件操作，
 * 自身只保留业务逻辑（改什么、怎么改）。
 */
class ApkFileToolRegistry(tools: Collection<ApkFileTool>) {

    private val byName: Map<String, ApkFileTool> = tools.associateBy(ApkFileTool::name)

    val names: Set<String> = byName.keys

    fun get(name: String): ApkFileTool? = byName[name]

    /** 便捷调用：执行工具并返回结果。 */
    fun execute(name: String, context: ToolContext, args: ToolArgs): ToolResult? = byName[name]?.execute(context, args)

    companion object {
        fun empty(): ApkFileToolRegistry = ApkFileToolRegistry(emptyList())
    }
}
