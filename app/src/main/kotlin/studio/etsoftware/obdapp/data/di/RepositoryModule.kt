package studio.etsoftware.obdapp.data.di

import studio.etsoftware.obdapp.data.repository.ConnectionRepositoryImpl
import studio.etsoftware.obdapp.data.repository.DashboardRepositoryImpl
import studio.etsoftware.obdapp.data.repository.DiagnosticsRepositoryImpl
import studio.etsoftware.obdapp.data.repository.DiscoveryRepositoryImpl
import studio.etsoftware.obdapp.domain.repository.ConnectionRepository
import studio.etsoftware.obdapp.domain.repository.DashboardRepository
import studio.etsoftware.obdapp.domain.repository.DiagnosticsRepository
import studio.etsoftware.obdapp.domain.repository.DiscoveryRepository
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
    abstract fun bindConnectionRepository(impl: ConnectionRepositoryImpl): ConnectionRepository

    @Binds
    @Singleton
    abstract fun bindDiscoveryRepository(impl: DiscoveryRepositoryImpl): DiscoveryRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(impl: DashboardRepositoryImpl): DashboardRepository

    @Binds
    @Singleton
    abstract fun bindDiagnosticsRepository(impl: DiagnosticsRepositoryImpl): DiagnosticsRepository
}
