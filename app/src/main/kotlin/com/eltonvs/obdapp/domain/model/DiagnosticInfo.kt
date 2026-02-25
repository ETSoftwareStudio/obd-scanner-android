package com.eltonvs.obdapp.domain.model

data class DiagnosticInfo(
    val vin: String = "",
    val troubleCodes: List<TroubleCode> = emptyList(),
    val milStatus: Boolean = false,
    val dtcCount: Int = 0,
)

data class TroubleCode(
    val code: String,
    val description: String,
    val type: TroubleCodeType,
)

enum class TroubleCodeType {
    CURRENT,
    PENDING,
    PERMANENT,
}
