package com.aeibi.design.di

import com.aeibi.design.data.securestore.AndroidKeystoreSecureStore
import com.aeibi.design.data.securestore.SecureStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecureStoreModule {
    @Binds
    @Singleton
    abstract fun bindSecureStore(implementation: AndroidKeystoreSecureStore): SecureStore
}
