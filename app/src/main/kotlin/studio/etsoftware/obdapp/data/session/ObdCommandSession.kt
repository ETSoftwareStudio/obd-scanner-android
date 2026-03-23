package studio.etsoftware.obdapp.data.session

import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.ObdResponse
import studio.etsoftware.obdapp.domain.model.TelemetryContext

interface ObdCommandSession {
    suspend fun run(
        context: TelemetryContext,
        cycleId: Long?,
        command: ObdCommand,
        preview: ((ObdResponse) -> String)? = null,
    ): ObdResponse

    fun previewValue(rawValue: String): String?
}
