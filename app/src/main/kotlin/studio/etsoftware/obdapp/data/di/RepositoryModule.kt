package studio.etsoftware.obdapp.data.di

import studio.etsoftware.obdapp.data.repository.ObdRepositoryImpl
import studio.etsoftware.obdapp.domain.repository.ConnectionRepository
import studio.etsoftware.obdapp.domain.repository.DashboardRepository
import studio.etsoftware.obdapp.domain.repository.DiagnosticsRepository
import studio.etsoftware.obdapp.domain.repository.DiscoveryRepository
import studio.etsoftware.obdapp.domain.repository.ObdRepository
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

    @Binds
    @Singleton
    abstract fun bindConnectionRepository(impl: ObdRepositoryImpl): ConnectionRepository

    @Binds
    @Singleton
    abstract fun bindDiscoveryRepository(impl: ObdRepositoryImpl): DiscoveryRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(impl: ObdRepositoryImpl): DashboardRepository

    @Binds
    @Singleton
    abstract fun bindDiagnosticsRepository(impl: ObdRepositoryImpl): DiagnosticsRepository
}
