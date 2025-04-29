package com.algorand.android.dependencyinjection

import com.algorand.android.repository.Arc200Repository
import com.algorand.android.repository.Arc200RepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
// Remove includes = [LocalAccountsModule::class]
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindArc200Repository(impl: Arc200RepositoryImpl): Arc200Repository
}
