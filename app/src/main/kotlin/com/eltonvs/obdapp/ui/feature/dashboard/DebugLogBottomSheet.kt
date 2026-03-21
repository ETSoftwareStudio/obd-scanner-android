package com.eltonvs.obdapp.ui.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eltonvs.obdapp.R
import com.eltonvs.obdapp.ui.theme.GaugeGreen
import com.eltonvs.obdapp.ui.theme.GaugeRed
import com.eltonvs.obdapp.util.LogEntry
import com.eltonvs.obdapp.util.LogType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogBottomSheet(
    logs: List<LogEntry>,
    onClearClick: () -> Unit,
    onExportClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Debug Log",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onExportClick, enabled = logs.isNotEmpty()) {
                    Text(stringResource(id = R.string.debug_log_export))
                }
                TextButton(onClick = onClearClick, enabled = logs.isNotEmpty()) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear")
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No logs yet.\nCommands and responses will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(logs) { entry ->
                        LogEntryItem(entry = entry)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LogEntryItem(entry: LogEntry) {
    val (backgroundColor, textColor) =
        when (entry.type) {
            LogType.COMMAND -> Color(0xFF1E3A5F) to Color(0xFF64B5F6)
            LogType.RESPONSE -> Color(0xFF1B3D1B) to GaugeGreen
            LogType.ERROR -> Color(0xFF3D1B1B) to GaugeRed
            LogType.SUCCESS -> Color(0xFF1B3D1B) to GaugeGreen
            LogType.INFO -> Color.Transparent to MaterialTheme.colorScheme.onSurface
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = entry.timestamp,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = Color.Gray,
            modifier = Modifier.width(90.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "[${entry.type.name}]",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.width(80.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = textColor,
            modifier = Modifier.weight(1f),
        )
    }
}
