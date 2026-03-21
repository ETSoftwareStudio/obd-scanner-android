package com.eltonvs.obdapp.data.di

import com.eltonvs.obdapp.data.telemetry.TelemetryRepositoryImpl
import com.eltonvs.obdapp.domain.repository.TelemetryRepository
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
