package studio.etsoftware.obdapp.data.connection

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class BluetoothTransport
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
    ) : ObdTransport {
        private var socket: BluetoothSocket? = null
        private var inputStream: InputStream? = null
        private var outputStream: OutputStream? = null

        private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override suspend fun connect(device: DeviceInfo): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    if (!hasBluetoothConnectPermission()) {
                        return@withContext Result.failure(Exception("BLUETOOTH_CONNECT permission is required"))
                    }

                    val bluetoothAdapter =
                        BluetoothAdapter.getDefaultAdapter()
                            ?: return@withContext Result.failure(Exception("Bluetooth not available"))

                    if (hasBluetoothScanPermission()) {
                        bluetoothAdapter.cancelDiscovery()
                    }

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
                } catch (_: Exception) {
                    // Ignore close exceptions
                } finally {
                    inputStream = null
                    outputStream = null
                    socket = null
                }
            }
        }

        private fun hasBluetoothConnectPermission(): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
        }

        private fun hasBluetoothScanPermission(): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_SCAN,
                ) == PackageManager.PERMISSION_GRANTED
        }

        override fun isConnected(): Boolean {
            return socket?.isConnected == true
        }

        override fun getInputStream(): InputStream? = inputStream

        override fun getOutputStream(): OutputStream? = outputStream
    }
