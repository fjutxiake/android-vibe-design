package com.aeibi.design.di

import java.io.File
import android.content.Context
import androidx.room3.Room
import com.aeibi.design.data.database.AppDatabase
import com.aeibi.design.data.projects.IconCopier
import com.aeibi.design.data.projects.ProjectRepository
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase = Room.databaseBuilder(
        context = context,
        klass = AppDatabase::class.java,
        name = DATABASE_NAME
    ).build()

    @Provides
    fun provideSessionDao(database: AppDatabase): SessionDao = database.sessionDao()

    @Provides
    @Singleton
    fun provideProjectsDir(@ApplicationContext context: Context): File =
        File(context.filesDir, "projects").also { it.mkdirs() }

    @Provides
    @Singleton
    fun provideProjectRepository(projectsDir: File, iconCopier: IconCopier): ProjectRepository =
        ProjectRepository(projectsDir, iconCopier)

    private const val DATABASE_NAME = "vibe-design.db"
}
