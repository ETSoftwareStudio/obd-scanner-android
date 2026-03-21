package com.eltonvs.obdapp.ui.feature.connection

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eltonvs.obdapp.domain.model.ConnectionState
import com.eltonvs.obdapp.domain.model.DeviceInfo
import com.eltonvs.obdapp.domain.model.DiscoveryState
import com.eltonvs.obdapp.ui.theme.ConnectionStatusConnected
import com.eltonvs.obdapp.ui.theme.ConnectionStatusDisconnected

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel = hiltViewModel(),
    onDeviceConnected: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val corePermissions = rememberCoreBluetoothPermissions()
    val discoveryPermissions = rememberDiscoveryPermissions()

    var hasCorePermissions by remember { mutableStateOf(hasAllPermissions(context, corePermissions)) }
    var hasDiscoveryPermissions by remember { mutableStateOf(hasAllPermissions(context, discoveryPermissions)) }
    var showRationale by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissionsMap ->
            hasCorePermissions = permissionsMap.values.all { it }
            if (!hasCorePermissions) {
                permissionDenied = true
            } else {
                viewModel.refreshSystemState()
                viewModel.loadPairedDevices()
            }
        }

    val discoveryPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissionsMap ->
            hasDiscoveryPermissions = permissionsMap.values.all { it }
            if (hasDiscoveryPermissions) {
                viewModel.startDiscovery()
            }
        }

    val enableBluetoothLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            viewModel.refreshSystemState()
            if (hasCorePermissions) {
                viewModel.loadPairedDevices()
            }
        }

    val enableLocationLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            viewModel.refreshSystemState()
        }

    LaunchedEffect(Unit) {
        if (!hasCorePermissions) {
            permissionLauncher.launch(corePermissions)
        } else {
            viewModel.refreshSystemState()
            viewModel.loadPairedDevices()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopDiscovery()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCorePermissions = hasAllPermissions(context, corePermissions)
        hasDiscoveryPermissions = hasAllPermissions(context, discoveryPermissions)
        if (hasCorePermissions) {
            viewModel.refreshSystemState()
            viewModel.loadPairedDevices()
        }
    }

    LaunchedEffect(uiState.connectionState) {
        if (uiState.connectionState is ConnectionState.Connected) {
            onDeviceConnected()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
            title = { Text("Bluetooth Permission Required") },
            text = {
                Text(
                    "This app needs Bluetooth scan and connect permissions to discover and connect to your OBD-II adapter.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRationale = false
                        permissionLauncher.launch(corePermissions)
                    },
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (permissionDenied) {
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Permissions Required") },
            text = {
                Text(
                    "Bluetooth permissions are required to discover nearby adapters and connect to them. Please enable them in Settings.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent =
                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            }
                        context.startActivity(intent)
                        permissionDenied = false
                    },
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        permissionDenied = false
                        showRationale = true
                    },
                ) {
                    Text("Ask Again")
                }
            },
        )
    }

    val canConnect =
        uiState.selectedDevice != null &&
            uiState.pairedDevices.any { it.address == uiState.selectedDevice?.address } &&
            !uiState.isLoading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect to OBD") },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.refreshSystemState()
                            viewModel.loadPairedDevices()
                        },
                        enabled = !uiState.isLoading && hasCorePermissions,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh paired devices")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
            ) {
                ConnectionStatusCard(
                    connectionState = uiState.connectionState,
                    selectedDevice = uiState.selectedDevice,
                )

                Spacer(modifier = Modifier.height(24.dp))

                when {
                    !hasCorePermissions -> {
                        PermissionRequiredCard(
                            onGrantPermissions = { showRationale = true },
                        )
                    }

                    !uiState.isBluetoothEnabled -> {
                        BluetoothDisabledCard(
                            onEnableBluetooth = {
                                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            },
                        )
                    }

                    else -> {
                        DeviceSection(
                            title = "Paired Devices",
                        ) {
                            when {
                                uiState.isLoadingPairedDevices -> {
                                    SectionLoadingState()
                                }

                                uiState.pairedDevices.isEmpty() -> {
                                    SectionEmptyState(
                                        message = "No paired devices found. Scan nearby devices, then pair your adapter in system Bluetooth settings.",
                                    )
                                }

                                else -> {
                                    DeviceList(
                                        devices = uiState.pairedDevices,
                                        isSelected = { device -> device.address == uiState.selectedDevice?.address },
                                        onClick = { device ->
                                            if (!uiState.isLoading) {
                                                viewModel.selectDevice(device)
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R && !hasDiscoveryPermissions) {
                        DeviceSection(
                            title = "Nearby Devices",
                            action = {
                                TextButton(
                                    onClick = { discoveryPermissionLauncher.launch(discoveryPermissions) },
                                    enabled = !uiState.isLoading,
                                ) {
                                    Text("Grant Location")
                                }
                            },
                        ) {
                            DiscoveryPermissionRequiredCard(
                                onGrantPermission = { discoveryPermissionLauncher.launch(discoveryPermissions) },
                            )
                        }
                    } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R && !uiState.isLocationServicesEnabledForDiscovery) {
                        DeviceSection(
                            title = "Nearby Devices",
                            action = {
                                TextButton(
                                    onClick = {
                                        enableLocationLauncher.launch(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                    },
                                    enabled = !uiState.isLoading,
                                ) {
                                    Text("Turn On Location")
                                }
                            },
                        ) {
                            LocationServicesRequiredCard(
                                onOpenLocationSettings = {
                                    enableLocationLauncher.launch(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                },
                            )
                        }
                    } else {
                        DeviceSection(
                            title = "Nearby Devices",
                            action = {
                                if (uiState.discoveryState is DiscoveryState.Starting || uiState.discoveryState is DiscoveryState.Discovering) {
                                    TextButton(
                                        onClick = { viewModel.stopDiscovery() },
                                        enabled = !uiState.isLoading,
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Stop")
                                    }
                                } else {
                                    TextButton(
                                        onClick = {
                                            if (hasDiscoveryPermissions) {
                                                viewModel.startDiscovery()
                                            } else {
                                                discoveryPermissionLauncher.launch(discoveryPermissions)
                                            }
                                        },
                                        enabled = !uiState.isLoading,
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Scan Nearby")
                                    }
                                }
                            },
                        ) {
                            when (val discoveryState = uiState.discoveryState) {
                                DiscoveryState.Idle -> {
                                    SectionEmptyState(
                                        message = "Tap Scan Nearby to search for nearby Bluetooth OBD adapters.",
                                    )
                                }

                                DiscoveryState.Starting -> {
                                    SectionLoadingState(message = "Starting Bluetooth discovery…")
                                }

                                is DiscoveryState.Discovering -> {
                                    if (uiState.nearbyDevices.isEmpty()) {
                                        SectionLoadingState(message = "Scanning for nearby devices…")
                                    } else {
                                        NearbyDeviceList(
                                            devices = uiState.nearbyDevices,
                                            pairingDeviceAddress = uiState.pairingDeviceAddress,
                                            onPairRequested = { device ->
                                                viewModel.pairDevice(device)
                                            },
                                        )
                                    }
                                }

                                is DiscoveryState.Finished -> {
                                    if (uiState.nearbyDevices.isEmpty()) {
                                        SectionEmptyState(
                                            message = "No nearby unpaired devices found. If your adapter is already paired, it will appear above.",
                                        )
                                    } else {
                                        NearbyDeviceList(
                                            devices = uiState.nearbyDevices,
                                            pairingDeviceAddress = uiState.pairingDeviceAddress,
                                            onPairRequested = { device ->
                                                viewModel.pairDevice(device)
                                            },
                                        )
                                    }
                                }

                                is DiscoveryState.Error -> {
                                    if (uiState.nearbyDevices.isEmpty()) {
                                        SectionEmptyState(message = discoveryState.message)
                                    } else {
                                        NearbyDeviceList(
                                            devices = uiState.nearbyDevices,
                                            pairingDeviceAddress = uiState.pairingDeviceAddress,
                                            onPairRequested = { device ->
                                                viewModel.pairDevice(device)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.connect() },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(56.dp),
                enabled = canConnect,
                shape = RoundedCornerShape(16.dp),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connecting...",
                        style = MaterialTheme.typography.titleMedium,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connect",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRequiredCard(
    onGrantPermissions: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Please grant Bluetooth permissions to discover nearby devices and connect to your adapter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onGrantPermissions) {
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
private fun DiscoveryPermissionRequiredCard(
    onGrantPermission: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Location permission required for scan",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "On Android 11 and below, Bluetooth discovery needs Location permission. Paired-device connection remains available without scanning.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onGrantPermission) {
                Text("Grant Location Permission")
            }
        }
    }
}

@Composable
private fun BluetoothDisabledCard(
    onEnableBluetooth: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Bluetooth is off",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enable Bluetooth to discover nearby adapters or connect to a paired device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onEnableBluetooth) {
                Text("Enable Bluetooth")
            }
        }
    }
}

@Composable
private fun LocationServicesRequiredCard(
    onOpenLocationSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Turn on Location to scan",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "On Android 11 and below, Bluetooth discovery needs Location services enabled. You can still connect to already paired devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onOpenLocationSettings) {
                Text("Open Location Settings")
            }
        }
    }
}

@Composable
private fun DeviceSection(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.invoke()
        }

        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SectionLoadingState(message: String = "Loading…") {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SectionEmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DeviceList(
    devices: List<DeviceInfo>,
    isSelected: (DeviceInfo) -> Boolean,
    onClick: (DeviceInfo) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        devices.forEach { device ->
            DeviceItem(
                device = device,
                isSelected = isSelected(device),
                onClick = { onClick(device) },
            )
        }
    }
}

@Composable
private fun NearbyDeviceList(
    devices: List<DeviceInfo>,
    pairingDeviceAddress: String?,
    onPairRequested: (DeviceInfo) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        devices.forEach { device ->
            DeviceItem(
                device = device,
                isSelected = false,
                onClick = { },
                trailingContent = {
                    TextButton(
                        onClick = { onPairRequested(device) },
                        enabled = pairingDeviceAddress == null,
                    ) {
                        if (pairingDeviceAddress == device.address) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pairing")
                        } else {
                            Text("Pair")
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    selectedDevice: DeviceInfo?,
) {
    val statusColor =
        when (connectionState) {
            is ConnectionState.Connected -> ConnectionStatusConnected
            is ConnectionState.Error -> MaterialTheme.colorScheme.error
            else -> ConnectionStatusDisconnected
        }

    val statusText =
        when (connectionState) {
            is ConnectionState.Connected -> "Connected"
            is ConnectionState.Connecting -> "Connecting..."
            is ConnectionState.Error -> connectionState.message
            else -> "Disconnected"
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(statusColor),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor,
                )
                if (selectedDevice != null) {
                    Text(
                        text = selectedDevice.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (connectionState is ConnectionState.Connected) {
                Icon(
                    imageVector = Icons.Default.BluetoothConnected,
                    contentDescription = null,
                    tint = statusColor,
                )
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: DeviceInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = trailingContent == null) { onClick() },
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(24.dp),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                trailingContent != null -> trailingContent()
                isSelected -> {
                    Box(
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberCoreBluetoothPermissions(): Array<String> {
    return remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        } else {
            emptyArray()
        }
    }
}

@Composable
private fun rememberDiscoveryPermissions(): Array<String> {
    return remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            emptyArray()
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}

private fun hasAllPermissions(
    context: Context,
    permissions: Array<String>,
): Boolean {
    return permissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
