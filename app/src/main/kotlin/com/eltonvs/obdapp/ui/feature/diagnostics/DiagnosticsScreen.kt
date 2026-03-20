package com.eltonvs.obdapp.ui.feature.diagnostics

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eltonvs.obdapp.domain.model.ConnectionState
import com.eltonvs.obdapp.domain.model.DiagnosticInfo
import com.eltonvs.obdapp.domain.model.TroubleCode
import com.eltonvs.obdapp.domain.model.TroubleCodeType
import com.eltonvs.obdapp.ui.feature.dashboard.DashboardViewModel
import com.eltonvs.obdapp.ui.theme.GaugeGreen
import com.eltonvs.obdapp.ui.theme.GaugeRed
import com.eltonvs.obdapp.ui.theme.GaugeYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DashboardViewModel,
    onConnectClick: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                actions = {
                    IconButton(
                        onClick = { viewModel.readDiagnostics() },
                        enabled = uiState.connectionState is ConnectionState.Connected,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
        ) {
            if (uiState.connectionState !is ConnectionState.Connected) {
                NotConnectedCard(onConnectClick = onConnectClick)
            } else {
                Button(
                    onClick = { viewModel.readDiagnostics() },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Read Diagnostics")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                uiState.diagnosticInfo?.let { info ->
                    DiagnosticContent(info = info)
                } ?: run {
                    if (!uiState.isLoading) {
                        InfoCard()
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticContent(info: DiagnosticInfo) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // VIN Card
        item {
            VinCard(vin = info.vin)
        }

        // Status Card
        item {
            StatusCard(info = info)
        }

        // DTCs
        if (info.troubleCodes.isNotEmpty()) {
            item {
                Text(
                    text = "Trouble Codes (${info.dtcCount})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(info.troubleCodes) { dtc ->
                DtcCard(dtc = dtc)
            }
        }
    }
}

@Composable
private fun VinCard(vin: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Vehicle Identification Number",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = vin.ifBlank { "N/A" },
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                color =
                    if (vin.isNotBlank()) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

@Composable
private fun StatusCard(info: DiagnosticInfo) {
    val (icon, color, text) =
        if (info.milStatus) {
            Triple(Icons.Default.Warning, GaugeRed, "Malfunction Indicator Lamp ON")
        } else {
            Triple(Icons.Default.CheckCircle, GaugeGreen, "No Issues Detected")
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = color.copy(alpha = 0.1f),
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Text(
                    text = "${info.dtcCount} trouble code(s) found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DtcCard(dtc: TroubleCode) {
    val typeColor =
        when (dtc.type) {
            TroubleCodeType.CURRENT -> GaugeRed
            TroubleCodeType.PENDING -> GaugeYellow
            TroubleCodeType.PERMANENT -> GaugeRed
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
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
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(typeColor),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dtc.code,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        ),
                )
                Text(
                    text = dtc.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier =
                    Modifier
                        .background(
                            color = typeColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = dtc.type.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = typeColor,
                )
            }
        }
    }
}

@Composable
private fun InfoCard() {
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
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Ready to Read",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Tap 'Read Diagnostics' to retrieve vehicle information",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotConnectedCard(onConnectClick: () -> Unit) {
    Card(
        modifier = Modifier
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
                imageVector = Icons.Default.Error,
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
                text = "Connect to an OBD device to read diagnostics",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
