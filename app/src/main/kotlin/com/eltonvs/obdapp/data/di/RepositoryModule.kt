package com.eltonvs.obdapp.di

import com.eltonvs.obdapp.data.repository.ObdRepositoryImpl
import com.eltonvs.obdapp.domain.repository.ObdRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindObdRepository(impl: ObdRepositoryImpl): ObdRepository
}
