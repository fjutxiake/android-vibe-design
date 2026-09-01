package com.aeibi.design.di

import android.content.Context
import androidx.room3.Room
import com.aeibi.design.data.database.AppDatabase
import com.aeibi.design.data.projectfiles.ProjectFileToolsFactory
import com.aeibi.design.data.projects.ProjectRepository
import com.aeibi.design.data.sessions.SessionDao
import com.aeibi.design.data.templates.TemplateRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton
import java.io.File

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
    fun provideProjectRepository(projectsDir: File, @ApplicationContext context: Context): ProjectRepository =
        ProjectRepository(projectsDir, context.contentResolver, context.assets)

    @Provides
    @Singleton
    fun provideProjectFileToolsFactory(projectsDir: File): ProjectFileToolsFactory =
        ProjectFileToolsFactory(projectsDir)

    @Provides
    @Singleton
    fun provideTemplateRepository(@ApplicationContext context: Context): TemplateRepository =
        TemplateRepository(context.assets)

    private const val DATABASE_NAME = "vibe-design.db"
}
