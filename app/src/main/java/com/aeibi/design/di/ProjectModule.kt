package com.aeibi.design.di

import com.aeibi.design.data.projects.ContentResolverIconCopier
import com.aeibi.design.data.projects.IconCopier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ProjectModule {

    @Binds
    abstract fun bindIconCopier(impl: ContentResolverIconCopier): IconCopier
}
