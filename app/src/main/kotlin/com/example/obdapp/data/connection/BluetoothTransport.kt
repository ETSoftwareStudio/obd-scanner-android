package com.example.obdapp.data.connection

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.example.obdapp.domain.model.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothTransport @Inject constructor() : ObdTransport {

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override suspend fun connect(device: DeviceInfo): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("Bluetooth not available"))

            val bluetoothDevice: BluetoothDevice = bluetoothAdapter.getRemoteDevice(device.address)

            socket?.close()
            socket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid)
            socket?.connect()

            inputStream = socket?.inputStream
            outputStream = socket?.outputStream

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                inputStream?.close()
                outputStream?.close()
                socket?.close()
            } catch (e: Exception) {
                // Ignore close exceptions
            } finally {
                inputStream = null
                outputStream = null
                socket = null
            }
        }
    }

    override fun isConnected(): Boolean {
        return socket?.isConnected == true
    }

    override fun getInputStream(): InputStream? = inputStream

    override fun getOutputStream(): OutputStream? = outputStream
}
