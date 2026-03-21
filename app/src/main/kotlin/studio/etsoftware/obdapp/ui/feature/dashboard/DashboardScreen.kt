package studio.etsoftware.obdapp.ui.feature.dashboard

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.etsoftware.obdapp.R
import studio.etsoftware.obdapp.domain.model.ConnectionState
import studio.etsoftware.obdapp.ui.components.GaugeCard
import studio.etsoftware.obdapp.ui.components.MetricCard
import studio.etsoftware.obdapp.ui.components.MetricIcon
import studio.etsoftware.obdapp.ui.theme.ConnectionStatusConnected
import studio.etsoftware.obdapp.ui.theme.ConnectionStatusDisconnected
import studio.etsoftware.obdapp.ui.theme.ConnectionStatusError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onConnectClick: () -> Unit = {},
    onDisconnected: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDebugLog by rememberSaveable { mutableStateOf(false) }
    var isDisconnecting by rememberSaveable { mutableStateOf(false) }

    val exportLogLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/plain"),
        ) { uri ->
            viewModel.exportLogs(uri)
        }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message =
                when (event) {
                    DashboardEvent.ExportSuccess -> context.getString(R.string.debug_log_export_success)
                    DashboardEvent.ExportSkippedNoLogs -> context.getString(R.string.debug_log_export_empty)
                    is DashboardEvent.ExportError -> {
                        if (event.reason.isNullOrBlank()) {
                            context.getString(R.string.debug_log_export_failed)
                        } else {
                            context.getString(R.string.debug_log_export_failed_with_reason, event.reason)
                        }
                    }
                }

            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(isDisconnecting, uiState.connectionState) {
        if (isDisconnecting && uiState.connectionState is ConnectionState.Disconnected) {
            isDisconnecting = false
            onDisconnected()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                actions = {
                    if (uiState.connectionState is ConnectionState.Connected) {
                        IconButton(
                            onClick = {
                                isDisconnecting = true
                                viewModel.disconnect()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.BluetoothDisabled,
                                contentDescription = "Disconnect",
                            )
                        }
                    }
                    ConnectionIndicator(
                        connectionState = uiState.connectionState,
                        onConnectClick = onConnectClick,
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDebugLog = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Debug Log",
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            if (uiState.connectionState !is ConnectionState.Connected) {
                NotConnectedCard(onConnectClick = onConnectClick)
            } else {
                // Gauges Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    GaugeCard(
                        value = uiState.speed.toFloatOrNull() ?: 0f,
                        maxValue = 200f,
                        label = "Speed",
                        unit = "km/h",
                        size = 140.dp,
                    )
                    GaugeCard(
                        value = (uiState.rpm.toFloatOrNull() ?: 0f) / 100f,
                        maxValue = 100f,
                        label = "RPM",
                        unit = "x100",
                        size = 140.dp,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Throttle Gauge
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    GaugeCard(
                        value = uiState.throttle.toFloatOrNull() ?: 0f,
                        maxValue = 100f,
                        label = "Throttle Position",
                        unit = "%",
                        size = 120.dp,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Metrics Grid
                Text(
                    text = "Live Sensors",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricCard(
                        label = "Coolant",
                        value = uiState.coolantTemp,
                        unit = "°C",
                        icon = MetricIcon.TEMPERATURE,
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = "Intake Air",
                        value = uiState.intakeTemp,
                        unit = "°C",
                        icon = MetricIcon.TEMPERATURE,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricCard(
                        label = "MAF",
                        value = uiState.maf,
                        unit = "g/s",
                        icon = MetricIcon.MAF,
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = "Fuel Level",
                        value = uiState.fuel,
                        unit = "%",
                        icon = MetricIcon.FUEL,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (showDebugLog) {
        DebugLogBottomSheet(
            logs = logs,
            onClearClick = viewModel::clearLogs,
            onExportClick = {
                exportLogLauncher.launch(viewModel.getSuggestedLogFileName())
            },
            onDismiss = { showDebugLog = false },
        )
    }
}

@Composable
private fun ConnectionIndicator(
    connectionState: ConnectionState,
    onConnectClick: () -> Unit,
) {
    val (color, text) =
        when (connectionState) {
            is ConnectionState.Connected -> ConnectionStatusConnected to "Connected"
            is ConnectionState.Connecting -> ConnectionStatusError to "Connecting..."
            is ConnectionState.Error -> ConnectionStatusError to "Error"
            else -> ConnectionStatusDisconnected to "Disconnected"
        }

    val isClickable = connectionState !is ConnectionState.Connected

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .padding(end = 8.dp)
                .then(
                    if (isClickable) {
                        Modifier.clickable { onConnectClick() }
                    } else {
                        Modifier
                    },
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun NotConnectedCard(onConnectClick: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onConnectClick() },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.BluetoothDisabled,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Not Connected",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Connect to an OBD device to see live metrics",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
