package com.aeibi.design.data.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** v1→v2：新增 messages 表，纯增量变更，sessions 表保持不动。 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override suspend fun migrate(connection: SQLiteConnection) {
            // 建表语句须与 AppDatabase_Impl 生成代码中的语句完全一致，否则 schema 校验会失败。
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `session_id` TEXT NOT NULL, " +
                    "`role` TEXT NOT NULL, `status` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_messages_session_id_created_at` ON `messages` (`session_id`, `created_at`)"
            )
        }
    }
