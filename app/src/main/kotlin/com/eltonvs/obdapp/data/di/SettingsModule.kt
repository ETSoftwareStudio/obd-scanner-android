package com.eltonvs.obdapp.data.di

import com.eltonvs.obdapp.data.settings.PollingSettingsRepositoryImpl
import com.eltonvs.obdapp.domain.repository.PollingSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindPollingSettingsRepository(impl: PollingSettingsRepositoryImpl): PollingSettingsRepository
}
