package com.aeibi.design.data.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v1→v2:新增 messages 表(entries 风格:id 主键、UNIQUE(session_id, seq) 排序、
 * type + payload JSON 结构化存储、FK 级联回 sessions)。sessions 表保持不动。
 * v2 从未发布,后续形状调整直接改这里,不产生 v3。
 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override suspend fun migrate(connection: SQLiteConnection) {
            // 建表语句须与 AppDatabase_Impl 生成代码中的语句完全一致，否则 schema 校验会失败。
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `session_id` TEXT NOT NULL, " +
                    "`seq` INTEGER NOT NULL, `type` TEXT NOT NULL, `payload` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`session_id`) REFERENCES `sessions`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            connection.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_session_id_seq` ON `messages` (`session_id`, `seq`)"
            )
        }
    }
