package com.aeibi.design.data.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.aeibi.design.data.sessions.SessionDao
import com.aeibi.design.data.sessions.SessionEntity

@Database(
  entities = [
    SessionEntity::class,
  ],
  version = 1,
)
abstract class AppDatabase : RoomDatabase() {

  abstract fun sessionDao(): SessionDao
}
