package com.eltonvs.obdapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eltonvs.obdapp.ui.theme.GaugeGreen
import com.eltonvs.obdapp.ui.theme.GaugeRed
import com.eltonvs.obdapp.ui.theme.GaugeYellow
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GaugeCard(
    value: Float,
    maxValue: Float,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    startAngle: Float = 135f,
    sweepAngle: Float = 270f
) {
    val normalizedValue = (value / maxValue).coerceIn(0f, 1f)
    val animatedValue by animateFloatAsState(
        targetValue = normalizedValue,
        animationSpec = tween(durationMillis = 500),
        label = "gauge_value"
    )

    val gaugeColor = when {
        normalizedValue > 0.8f -> GaugeRed
        normalizedValue > 0.6f -> GaugeYellow
        else -> GaugeGreen
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size)
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val strokeWidth = 20.dp.toPx()
                val radius = (size.toPx() - strokeWidth) / 2
                val center = Offset(size.toPx() / 2, size.toPx() / 2)

                // Background arc
                drawArc(
                    color = Color.Gray.copy(alpha = 0.3f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Value arc
                drawArc(
                    color = gaugeColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * animatedValue,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Needle indicator
                val needleAngle = Math.toRadians((startAngle + sweepAngle * animatedValue).toDouble())
                val needleLength = radius * 0.7f
                val needleEndX = center.x + needleLength * cos(needleAngle).toFloat()
                val needleEndY = center.y + needleLength * sin(needleAngle).toFloat()
                
                drawLine(
                    color = gaugeColor,
                    start = center,
                    end = Offset(needleEndX, needleEndY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value.toInt().toString(),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
