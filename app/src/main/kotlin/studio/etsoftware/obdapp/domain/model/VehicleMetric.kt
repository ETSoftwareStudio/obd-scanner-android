package studio.etsoftware.obdapp.domain.model

data class VehicleMetric(
    val name: String,
    val value: String,
    val unit: String,
    val minValue: Float = 0f,
    val maxValue: Float = 100f,
)

data class VehicleSpeed(
    val speed: Float,
    val unit: String = "km/h",
)

data class EngineRpm(
    val rpm: Int,
    val unit: String = "RPM",
)

data class ThrottlePosition(
    val position: Float,
    val unit: String = "%",
)

data class IntakeAirTemperature(
    val temperature: Float,
    val unit: String = "°C",
)

data class CoolantTemperature(
    val temperature: Float,
    val unit: String = "°C",
)

data class MassAirFlow(
    val maf: Float,
    val unit: String = "g/s",
)

data class FuelLevel(
    val level: Float,
    val unit: String = "%",
)

data class EngineRunTime(
    val runtime: String,
)
