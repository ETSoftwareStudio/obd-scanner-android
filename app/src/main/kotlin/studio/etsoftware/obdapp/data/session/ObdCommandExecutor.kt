package studio.etsoftware.obdapp.data.session

import android.os.SystemClock
import studio.etsoftware.obdapp.data.telemetry.TelemetryRecorder
import studio.etsoftware.obdapp.domain.model.CommandTelemetry
import studio.etsoftware.obdapp.domain.model.TelemetryContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObdCommandExecutor
    @Inject
    constructor(
        private val telemetryRecorder: TelemetryRecorder,
    ) {
        suspend fun <T> execute(
            context: TelemetryContext,
            cycleId: Long?,
            rawPid: String,
            commandName: String,
            block: suspend () -> T,
            preview: (T) -> String? = { null },
        ): T {
            val startedAtWall = System.currentTimeMillis()
            val startedAtMono = SystemClock.elapsedRealtime()

            try {
                val result = block()
                val finishedAtWall = System.currentTimeMillis()
                val finishedAtMono = SystemClock.elapsedRealtime()

                telemetryRecorder.recordCommand(
                    CommandTelemetry(
                        sessionId = telemetryRecorder.currentSessionId(),
                        cycleId = cycleId,
                        context = context,
                        commandName = commandName,
                        rawPid = rawPid,
                        startedAtMs = startedAtWall,
                        finishedAtMs = finishedAtWall,
                        durationMs = finishedAtMono - startedAtMono,
                        success = true,
                        valuePreview = previewValue(preview(result)),
                    ),
                )

                return result
            } catch (e: Exception) {
                val finishedAtWall = System.currentTimeMillis()
                val finishedAtMono = SystemClock.elapsedRealtime()

                telemetryRecorder.recordCommand(
                    CommandTelemetry(
                        sessionId = telemetryRecorder.currentSessionId(),
                        cycleId = cycleId,
                        context = context,
                        commandName = commandName,
                        rawPid = rawPid,
                        startedAtMs = startedAtWall,
                        finishedAtMs = finishedAtWall,
                        durationMs = finishedAtMono - startedAtMono,
                        success = false,
                        errorType = e::class.simpleName ?: "Exception",
                        errorMessage = previewValue(e.message),
                    ),
                )

                throw e
            }
        }

        fun previewValue(value: String?): String? =
            value
                ?.replace('\n', ' ')
                ?.replace('\r', ' ')
                ?.trim()
                ?.take(40)
    }
