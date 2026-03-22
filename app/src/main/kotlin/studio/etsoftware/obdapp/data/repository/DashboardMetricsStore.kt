package studio.etsoftware.obdapp.data.repository

import studio.etsoftware.obdapp.data.telemetry.TelemetryRecorder
import studio.etsoftware.obdapp.domain.model.DashboardMetricsSnapshot
import studio.etsoftware.obdapp.domain.model.MetricEmissionTelemetry
import studio.etsoftware.obdapp.domain.model.VehicleMetric
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class DashboardMetricsStore
    @Inject
    constructor(
        private val telemetryRecorder: TelemetryRecorder,
    ) {
        private val metricsFlow = MutableSharedFlow<VehicleMetric>(replay = 1, extraBufferCapacity = 64)
        private val dashboardState = MutableStateFlow(DashboardMetricsSnapshot())

        val vehicleMetrics: Flow<VehicleMetric> = metricsFlow.asSharedFlow()
        val dashboardMetrics: StateFlow<DashboardMetricsSnapshot> = dashboardState.asStateFlow()

        suspend fun publish(
            cycleId: Long?,
            metricId: DashboardMetricId,
            value: String,
            unit: String,
            minValue: Float,
            maxValue: Float,
        ) {
            val metricName = metricDisplayName(metricId)
            metricsFlow.emit(
                VehicleMetric(
                    name = metricName,
                    value = value,
                    unit = unit,
                    minValue = minValue,
                    maxValue = maxValue,
                ),
            )
            dashboardState.value = updateDashboardSnapshot(metricId, value)
            telemetryRecorder.recordMetricEmission(
                MetricEmissionTelemetry(
                    sessionId = telemetryRecorder.currentSessionId(),
                    cycleId = cycleId,
                    metricName = metricName,
                    emittedAtMs = System.currentTimeMillis(),
                    value = value.previewValue() ?: value,
                ),
            )
        }

        private fun updateDashboardSnapshot(
            metricId: DashboardMetricId,
            value: String,
        ): DashboardMetricsSnapshot {
            return when (metricId) {
                DashboardMetricId.SPEED -> dashboardState.value.copy(speed = value)
                DashboardMetricId.RPM -> dashboardState.value.copy(rpm = value)
                DashboardMetricId.THROTTLE -> dashboardState.value.copy(throttle = value)
                DashboardMetricId.MAF -> dashboardState.value.copy(maf = value)
                DashboardMetricId.COOLANT -> dashboardState.value.copy(coolantTemp = value)
                DashboardMetricId.INTAKE -> dashboardState.value.copy(intakeTemp = value)
                DashboardMetricId.FUEL -> dashboardState.value.copy(fuel = value)
            }
        }

        private fun metricDisplayName(metricId: DashboardMetricId): String {
            return when (metricId) {
                DashboardMetricId.SPEED -> "Speed"
                DashboardMetricId.RPM -> "RPM"
                DashboardMetricId.THROTTLE -> "Throttle"
                DashboardMetricId.MAF -> "MAF"
                DashboardMetricId.COOLANT -> "Coolant"
                DashboardMetricId.INTAKE -> "Intake"
                DashboardMetricId.FUEL -> "Fuel"
            }
        }

        private fun String.previewValue(): String? {
            return replace('\n', ' ')
                .replace('\r', ' ')
                .trim()
                .take(40)
                .takeIf { it.isNotBlank() }
        }
    }
