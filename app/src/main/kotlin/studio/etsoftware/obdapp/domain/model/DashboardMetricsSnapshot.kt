package studio.etsoftware.obdapp.domain.model

data class DashboardMetricsSnapshot(
    val speed: String = "",
    val rpm: String = "",
    val throttle: String = "",
    val coolantTemp: String = "",
    val intakeTemp: String = "",
    val maf: String = "",
    val fuel: String = "",
)
