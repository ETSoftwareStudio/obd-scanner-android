package studio.etsoftware.obdapp.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.github.eltonvs.obd.command.Switcher
import com.github.eltonvs.obd.command.at.ResetAdapterCommand
import com.github.eltonvs.obd.command.at.SetEchoCommand
import com.github.eltonvs.obd.connection.ObdDeviceConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import studio.etsoftware.obdapp.data.connection.ObdTransport
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.model.DeviceType
import studio.etsoftware.obdapp.domain.model.TelemetryContext
import studio.etsoftware.obdapp.util.LogManager

@Singleton
class ObdSessionManager
    @Inject
    constructor(
        @param:ApplicationContext private val appContext: Context,
        private val transport: ObdTransport,
        private val logManager: LogManager,
        private val commandExecutor: ObdCommandExecutor,
    ) {
        private var obdConnection: ObdDeviceConnection? = null
        private val connectionAccessMutex = Mutex()
        private val mutableConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

        val connectionState: StateFlow<ConnectionState> = mutableConnectionState.asStateFlow()

        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        suspend fun getPairedDevices(): List<DeviceInfo> =
            withContext(Dispatchers.IO) {
                if (!hasBluetoothConnectPermission()) {
                    logManager.error("Missing BLUETOOTH_CONNECT permission")
                    return@withContext emptyList()
                }

                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext emptyList()
                if (!adapter.isEnabled) {
                    logManager.error("Bluetooth is disabled")
                    return@withContext emptyList()
                }

                try {
                    adapter.bondedDevices?.map { bluetoothDevice ->
                        DeviceInfo(
                            address = bluetoothDevice.address,
                            name = bluetoothDevice.name ?: "Unknown Device",
                            type = DeviceType.CLASSIC,
                        )
                    } ?: emptyList()
                } catch (_: SecurityException) {
                    logManager.error("Unable to read paired devices without BLUETOOTH_CONNECT permission")
                    emptyList()
                }
            }

        suspend fun connect(device: DeviceInfo): Result<Unit> {
            mutableConnectionState.value = ConnectionState.Connecting
            logManager.info("Connecting to ${device.name} (${device.address})...")

            if (!hasBluetoothConnectPermission()) {
                val error = Exception("BLUETOOTH_CONNECT permission is required")
                mutableConnectionState.value = ConnectionState.Error(error.message ?: "Missing permission")
                logManager.error(error.message ?: "Missing BLUETOOTH_CONNECT permission")
                return Result.failure(error)
            }

            val connectionResult = transport.connect(device)
            if (connectionResult.isFailure) {
                val errorMsg = connectionResult.exceptionOrNull()?.message ?: "Connection failed"
                mutableConnectionState.value = ConnectionState.Error(errorMsg)
                logManager.error("Connection failed: $errorMsg")
                return Result.failure(connectionResult.exceptionOrNull() ?: Exception("Connection failed"))
            }

            val inputStream = transport.getInputStream()
            val outputStream = transport.getOutputStream()
            if (inputStream == null || outputStream == null) {
                val error = Exception("Failed to get streams")
                mutableConnectionState.value = ConnectionState.Error(error.message ?: "Failed to get streams")
                logManager.error("Failed to get Bluetooth streams")
                return Result.failure(error)
            }

            obdConnection = ObdDeviceConnection(inputStream, outputStream)

            return try {
                withConnectionAccess {
                    logManager.command("ATZ (Reset adapter)")
                    commandExecutor.execute(
                        context = TelemetryContext.INIT,
                        cycleId = null,
                        rawPid = "ATZ",
                        commandName = "ResetAdapterCommand",
                        block = { obdConnection?.run(ResetAdapterCommand()) ?: error("No OBD connection") },
                        preview = { it.value },
                    )

                    logManager.command("ATE0 (Echo off)")
                    commandExecutor.execute(
                        context = TelemetryContext.INIT,
                        cycleId = null,
                        rawPid = "ATE0",
                        commandName = "SetEchoCommand",
                        block = { obdConnection?.run(SetEchoCommand(Switcher.OFF)) ?: error("No OBD connection") },
                        preview = { it.value },
                    )
                }

                mutableConnectionState.value = ConnectionState.Connected
                logManager.success("Connected to ${device.name}")
                Result.success(Unit)
            } catch (e: Exception) {
                mutableConnectionState.value = ConnectionState.Error(e.message ?: "Failed to initialize OBD")
                logManager.error("OBD initialization failed: ${e.message}")
                Result.failure(e)
            }
        }

        suspend fun disconnect() {
            withConnectionAccess {
                obdConnection = null
                transport.disconnect()
            }
            mutableConnectionState.value = ConnectionState.Disconnected
        }

        fun currentConnection(): ObdDeviceConnection? = obdConnection

        fun isTransportConnected(): Boolean = transport.isConnected()

        suspend fun <T> withConnectionAccess(block: suspend () -> T): T {
            return connectionAccessMutex.withLock {
                block()
            }
        }

        private fun hasBluetoothConnectPermission(): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
        }
    }
