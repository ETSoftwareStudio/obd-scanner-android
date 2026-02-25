package com.eltonvs.obdapp.ui.feature.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.eltonvs.obdapp.domain.model.ConnectionState
import com.eltonvs.obdapp.ui.components.GaugeCard
import com.eltonvs.obdapp.ui.components.MetricCard
import com.eltonvs.obdapp.ui.components.MetricIcon
import com.eltonvs.obdapp.ui.theme.ConnectionStatusConnected
import com.eltonvs.obdapp.ui.theme.ConnectionStatusDisconnected
import com.eltonvs.obdapp.ui.theme.ConnectionStatusError
import com.eltonvs.obdapp.ui.theme.DashboardBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = DashboardBackground,
                    ),
                actions = {
                    ConnectionIndicator(connectionState = uiState.connectionState)
                },
            )
        },
        containerColor = DashboardBackground,
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
                NotConnectedCard()
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
                        value = (uiState.rpm.toFloatOrNull() ?: 0f) / 60f,
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
}

@Composable
private fun ConnectionIndicator(connectionState: ConnectionState) {
    val (color, text) =
        when (connectionState) {
            is ConnectionState.Connected -> ConnectionStatusConnected to "Connected"
            is ConnectionState.Connecting -> ConnectionStatusError to "Connecting..."
            is ConnectionState.Error -> ConnectionStatusError to "Error"
            else -> ConnectionStatusDisconnected to "Disconnected"
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 8.dp),
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
private fun NotConnectedCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
