package com.aeibi.design.di

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import android.content.Context
import androidx.room3.Room
import com.aeibi.design.data.database.AppDatabase
import com.aeibi.design.data.projects.ProjectRepository
import com.aeibi.design.data.sessions.SessionDao
import com.aeibi.design.data.templates.TemplateRepository
import com.aeibi.design.data.versions.GitVersionStorage
import com.aeibi.design.data.versions.VersionSnapshotService
import com.aeibi.design.data.versions.VersionStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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
    ).fallbackToDestructiveMigration().build()

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
    fun provideTemplateRepository(@ApplicationContext context: Context): TemplateRepository =
        TemplateRepository(context.assets)

    @Provides
    @Singleton
    fun provideKoogHttpClientFactory(): KoogHttpClient.Factory = KtorKoogHttpClient.Factory(HttpClient(OkHttp))

    @Provides
    @Singleton
    fun provideVersionStorage(projectRepository: ProjectRepository): VersionStorage =
        GitVersionStorage(projectRepository)

    @Provides
    @Singleton
    fun provideVersionSnapshotService(
        storage: VersionStorage,
        projectRepository: ProjectRepository
    ): VersionSnapshotService = VersionSnapshotService(storage, projectRepository)

    private const val DATABASE_NAME = "vibe-design.db"
}
