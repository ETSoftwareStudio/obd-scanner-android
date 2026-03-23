package studio.etsoftware.obdapp.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import studio.etsoftware.obdapp.data.connection.BluetoothDiscoveryDataSource
import studio.etsoftware.obdapp.data.connection.BluetoothDiscoveryManager
import studio.etsoftware.obdapp.data.session.ObdSessionDataSource
import studio.etsoftware.obdapp.data.session.ObdSessionManager

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {
    @Binds
    @Singleton
    abstract fun bindBluetoothDiscoveryDataSource(
        bluetoothDiscoveryManager: BluetoothDiscoveryManager,
    ): BluetoothDiscoveryDataSource

    @Binds
    @Singleton
    abstract fun bindObdSessionDataSource(
        obdSessionManager: ObdSessionManager,
    ): ObdSessionDataSource
}
