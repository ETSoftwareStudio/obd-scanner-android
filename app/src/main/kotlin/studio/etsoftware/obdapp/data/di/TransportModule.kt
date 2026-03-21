package studio.etsoftware.obdapp.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import studio.etsoftware.obdapp.data.connection.BluetoothTransport
import studio.etsoftware.obdapp.data.connection.ObdTransport

@Module
@InstallIn(SingletonComponent::class)
abstract class TransportModule {
    @Binds
    @Singleton
    abstract fun bindObdTransport(bluetoothTransport: BluetoothTransport): ObdTransport
}
