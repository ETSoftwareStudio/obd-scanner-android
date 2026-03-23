package studio.etsoftware.obdapp.data.diagnostics

import com.github.eltonvs.obd.command.control.ResetTroubleCodesCommand
import com.github.eltonvs.obd.command.control.TroubleCodesCommand
import com.github.eltonvs.obd.command.control.VINCommand
import javax.inject.Inject
import javax.inject.Singleton
import studio.etsoftware.obdapp.data.logging.LogManager
import studio.etsoftware.obdapp.data.session.ObdCommandExecutor
import studio.etsoftware.obdapp.data.session.ObdSessionDataSource
import studio.etsoftware.obdapp.domain.model.DiagnosticInfo
import studio.etsoftware.obdapp.domain.model.TelemetryContext
import studio.etsoftware.obdapp.domain.model.TroubleCode
import studio.etsoftware.obdapp.domain.model.TroubleCodeType

@Singleton
class DiagnosticsService
    @Inject
    constructor(
        private val commandExecutor: ObdCommandExecutor,
        private val dtcParser: DtcParser,
        private val logManager: LogManager,
        private val sessionDataSource: ObdSessionDataSource,
    ) {
        suspend fun readDiagnosticInfo(): Result<DiagnosticInfo> =
            sessionDataSource.withConnectedSession { connection ->
                val vin =
                    commandExecutor.execute(
                        context = TelemetryContext.DIAGNOSTICS,
                        cycleId = null,
                        rawPid = "VIN",
                        commandName = "VINCommand",
                        block = { connection.run(VINCommand()) },
                        preview = { response -> response.value },
                    )

                val troubleCodes =
                    commandExecutor.execute(
                        context = TelemetryContext.DIAGNOSTICS,
                        cycleId = null,
                        rawPid = "03",
                        commandName = "TroubleCodesCommand",
                        block = { connection.run(TroubleCodesCommand()) },
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
            sessionDataSource.withConnectedSession { connection ->
                logManager.command("04 (Clear trouble codes)")
                commandExecutor.execute(
                    context = TelemetryContext.CLEAR_DTC,
                    cycleId = null,
                    rawPid = "04",
                    commandName = "ResetTroubleCodesCommand",
                    block = { connection.run(ResetTroubleCodesCommand()) },
                )
                logManager.success("Trouble codes clear command sent")
                Result.success(Unit)
            }
    }
