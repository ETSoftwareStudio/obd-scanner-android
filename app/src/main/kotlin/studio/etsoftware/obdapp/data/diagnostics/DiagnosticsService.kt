package studio.etsoftware.obdapp.data.diagnostics

import com.github.eltonvs.obd.command.control.ResetTroubleCodesCommand
import com.github.eltonvs.obd.command.control.TroubleCodesCommand
import com.github.eltonvs.obd.command.control.VINCommand
import javax.inject.Inject
import javax.inject.Singleton
import studio.etsoftware.obdapp.data.logging.LogManager
import studio.etsoftware.obdapp.data.session.ObdSessionDataSource
import studio.etsoftware.obdapp.domain.model.DiagnosticInfo
import studio.etsoftware.obdapp.domain.model.TelemetryContext
import studio.etsoftware.obdapp.domain.model.TroubleCode
import studio.etsoftware.obdapp.domain.model.TroubleCodeType

@Singleton
class DiagnosticsService
    @Inject
    constructor(
        private val dtcParser: DtcParser,
        private val logManager: LogManager,
        private val sessionDataSource: ObdSessionDataSource,
    ) {
        suspend fun readDiagnosticInfo(): Result<DiagnosticInfo> =
            sessionDataSource.withConnectedSession { session ->
                val vin =
                    session.run(
                        context = TelemetryContext.DIAGNOSTICS,
                        cycleId = null,
                        command = VINCommand(),
                        preview = { response -> response.value },
                    )

                val troubleCodes =
                    session.run(
                        context = TelemetryContext.DIAGNOSTICS,
                        cycleId = null,
                        command = TroubleCodesCommand(),
                        preview = { response -> response.value },
                    )

                val codes = dtcParser.parse(troubleCodes.value)

                Result.success(
                    DiagnosticInfo(
                        vin = vin.value,
                        troubleCodes =
                            codes.map { code ->
                                TroubleCode(code, dtcParser.descriptionFor(code), TroubleCodeType.CURRENT)
                            },
                        milStatus = codes.isNotEmpty(),
                        dtcCount = codes.size,
                    ),
                )
            }

        suspend fun clearTroubleCodes(): Result<Unit> =
            sessionDataSource.withConnectedSession { session ->
                logManager.command("04 (Clear trouble codes)")
                session.run(
                    context = TelemetryContext.CLEAR_DTC,
                    cycleId = null,
                    command = ResetTroubleCodesCommand(),
                )
                logManager.success("Trouble codes clear command sent")
                Result.success(Unit)
            }
    }
