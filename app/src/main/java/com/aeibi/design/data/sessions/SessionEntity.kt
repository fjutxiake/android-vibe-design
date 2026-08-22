package com.aeibi.design.data.sessions

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
  tableName = "sessions",
  indices = [
    Index(value = ["project_id", "updated_at"]),
  ],
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
)
