package com.eltonvs.obdapp.ui.feature.dashboard

sealed interface DashboardEvent {
    data object ExportSuccess : DashboardEvent

    data object ExportSkippedNoLogs : DashboardEvent

    data class ExportError(
        val reason: String? = null,
    ) : DashboardEvent
}
