package studio.etsoftware.obdapp.data.session

import android.content.Context
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
import studio.etsoftware.obdapp.data.connection.BluetoothAccess
import studio.etsoftware.obdapp.data.connection.ObdTransport
import studio.etsoftware.obdapp.data.logging.LogManager
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.domain.model.DeviceInfo
import studio.etsoftware.obdapp.domain.model.DeviceType
import studio.etsoftware.obdapp.domain.model.TelemetryContext

@Singleton
class ObdSessionManager
    @Inject
    constructor(
        @param:ApplicationContext private val appContext: Context,
        private val transport: ObdTransport,
        private val logManager: LogManager,
        private val commandExecutor: ObdCommandExecutor,
    ) : ObdSessionDataSource {
        private val bluetoothAccess = BluetoothAccess(appContext)

        private var obdConnection: ObdDeviceConnection? = null
        private val connectionAccessMutex = Mutex()
        private val mutableConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

        override val connectionState: StateFlow<ConnectionState> = mutableConnectionState.asStateFlow()

        override suspend fun getPairedDevices(): List<DeviceInfo> =
            withContext(Dispatchers.IO) {
                if (!bluetoothAccess.hasBluetoothConnectPermission()) {
                    logManager.error("Missing BLUETOOTH_CONNECT permission")
                    return@withContext emptyList()
                }

                val adapter = bluetoothAccess.adapter ?: return@withContext emptyList()
                if (!adapter.isEnabled) {
                    logManager.error("Bluetooth is disabled")
                    return@withContext emptyList()
                }

                try {
                    adapter.bondedDevices.map { bluetoothDevice ->
                        DeviceInfo(
                            address = bluetoothDevice.address,
                            name = bluetoothDevice.name ?: "Unknown Device",
                            type = DeviceType.CLASSIC,
                        )
                    }
                } catch (_: SecurityException) {
                    logManager.error("Unable to read paired devices without BLUETOOTH_CONNECT permission")
                    emptyList()
                }
            }

        override suspend fun connect(device: DeviceInfo): Result<Unit> {
            mutableConnectionState.value = ConnectionState.Connecting
            logManager.info("Connecting to ${device.name} (${device.address})...")

            if (!bluetoothAccess.hasBluetoothConnectPermission()) {
                val error = Exception("BLUETOOTH_CONNECT permission is required")
                mutableConnectionState.value = ConnectionState.Error(error.message ?: "Missing permission")
                logManager.error(error.message ?: "Missing BLUETOOTH_CONNECT permission")
                return Result.failure(error)
            }

            val connectionResult = transport.connect(device)
            if (connectionResult.isFailure) {
                cleanupConnection()
                val cause = connectionResult.exceptionOrNull() ?: Exception("Connection failed")
                val errorMsg = cause.message ?: "Connection failed"
                mutableConnectionState.value = ConnectionState.Error(errorMsg)
                logManager.error("Connection failed: $errorMsg")
                return Result.failure(cause)
            }

            val inputStream = transport.getInputStream()
            val outputStream = transport.getOutputStream()
            if (inputStream == null || outputStream == null) {
                val error = Exception("Failed to get streams")
                cleanupConnection()
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
                cleanupConnection()
                mutableConnectionState.value = ConnectionState.Error(e.message ?: "Failed to initialize OBD")
                logManager.error("OBD initialization failed: ${e.message}")
                Result.failure(e)
            }
        }

        override suspend fun disconnect() {
            cleanupConnection()
            mutableConnectionState.value = ConnectionState.Disconnected
        }

        override suspend fun <T> withConnectedSession(block: suspend (ObdDeviceConnection) -> Result<T>): Result<T> =
            connectionAccessMutex.withLock {
                val connection = obdConnection ?: return@withLock Result.failure(Exception("Not connected"))
                if (!transport.isConnected()) {
                    return@withLock Result.failure(Exception("Transport is disconnected"))
                }
                block(connection)
            }

        @Deprecated("Use withConnectedSession instead to keep connection ownership inside the session layer")
        override fun currentConnection(): ObdDeviceConnection? = obdConnection

        override fun isTransportConnected(): Boolean = transport.isConnected()

        @Deprecated("Use withConnectedSession instead to keep connection ownership inside the session layer")
        override suspend fun <T> withConnectionAccess(block: suspend () -> T): T =
            connectionAccessMutex.withLock {
                block()
            }

        private suspend fun cleanupConnection() {
            withConnectionAccess {
                obdConnection = null
                transport.disconnect()
            }
        }
    }
