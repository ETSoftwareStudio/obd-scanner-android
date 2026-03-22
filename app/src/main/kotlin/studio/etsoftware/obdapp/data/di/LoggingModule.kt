package studio.etsoftware.obdapp.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import studio.etsoftware.obdapp.data.logging.DebugLogRepositoryImpl
import studio.etsoftware.obdapp.data.logging.LogExportRepositoryImpl
import studio.etsoftware.obdapp.domain.repository.DebugLogRepository
import studio.etsoftware.obdapp.domain.repository.LogExportRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class LoggingModule {
    @Binds
    @Singleton
    abstract fun bindDebugLogRepository(impl: DebugLogRepositoryImpl): DebugLogRepository

    @Binds
    @Singleton
    abstract fun bindLogExportRepository(impl: LogExportRepositoryImpl): LogExportRepository
}
