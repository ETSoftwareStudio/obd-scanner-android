package studio.etsoftware.obdapp.data.di

import studio.etsoftware.obdapp.data.settings.AppSettingsRepositoryImpl
import studio.etsoftware.obdapp.data.settings.PollingSettingsRepositoryImpl
import studio.etsoftware.obdapp.domain.repository.AppSettingsRepository
import studio.etsoftware.obdapp.domain.repository.PollingSettingsRepository
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

    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(impl: AppSettingsRepositoryImpl): AppSettingsRepository
}
