package com.aeibi.design.data.sessions

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["project_id", "updated_at"])
    ]
)
data class SessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "project_id")
    val projectId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    /** 会话绑定的 provider 配置 id:会话创建时从全局默认继承,之后全局变化不影响本会话。null = 未绑定。 */
    @ColumnInfo(name = "provider_config_id")
    val providerConfigId: String? = null,
    /** 会话绑定的模型名,与 [providerConfigId] 成对。null = 未绑定。 */
    @ColumnInfo(name = "model")
    val model: String? = null
)
