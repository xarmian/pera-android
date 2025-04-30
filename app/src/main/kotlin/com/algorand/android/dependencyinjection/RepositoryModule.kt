package com.algorand.android.dependencyinjection

import com.algorand.android.repository.Arc200Repository
import com.algorand.android.repository.Arc200RepositoryImpl
import com.algorand.android.repository.NftRepository
import com.algorand.android.repository.NftRepositoryImpl
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

    @Binds
    @Singleton
    abstract fun bindNftRepository(nftRepositoryImpl: NftRepositoryImpl): NftRepository

    // Add other repository bindings here if needed
}
