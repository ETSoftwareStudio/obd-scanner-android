package studio.etsoftware.obdapp.data.connection

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal class BluetoothAccess(
    private val context: Context,
) {
    private val bluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }

    val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)

    fun hasBluetoothScanPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            isPermissionGranted(Manifest.permission.BLUETOOTH_SCAN)

    fun hasLocationPermission(): Boolean =
        isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
