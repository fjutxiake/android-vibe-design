package com.aeibi.design.di

import android.content.Context
import androidx.room3.Room
import com.aeibi.design.data.database.AppDatabase
import com.aeibi.design.data.sessions.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

  @Provides
  @Singleton
  fun provideAppDatabase(
    @ApplicationContext context: Context,
  ): AppDatabase = Room.databaseBuilder(
    context = context,
    klass = AppDatabase::class.java,
    name = DATABASE_NAME,
  ).build()

  @Provides
  fun provideSessionDao(database: AppDatabase): SessionDao = database.sessionDao()

  private const val DATABASE_NAME = "vibe-design.db"
}
