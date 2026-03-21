package studio.etsoftware.obdapp.data.di

import studio.etsoftware.obdapp.data.telemetry.TelemetryRepositoryImpl
import studio.etsoftware.obdapp.domain.repository.TelemetryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryModule {
    @Binds
    @Singleton
    abstract fun bindTelemetryRepository(impl: TelemetryRepositoryImpl): TelemetryRepository
}
