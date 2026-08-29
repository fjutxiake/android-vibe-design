package com.aeibi.design.data.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.aeibi.design.data.messages.MessageDao
import com.aeibi.design.data.messages.MessageEntryEntity
import com.aeibi.design.data.sessions.SessionDao
import com.aeibi.design.data.sessions.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        MessageEntryEntity::class
    ],
    version = 2
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun messageDao(): MessageDao
}
