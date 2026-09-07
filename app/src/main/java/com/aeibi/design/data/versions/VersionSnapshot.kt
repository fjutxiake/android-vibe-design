package com.aeibi.design.data.versions

/** 一条版本快照。[id] 在 git 存储下即提交的 SHA-1。 */
data class VersionSnapshot(
    val id: String,
    val projectId: String,
    val label: String,
    val createdAt: Long,
    val trigger: VersionTrigger
)
