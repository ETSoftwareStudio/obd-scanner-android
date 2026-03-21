package studio.etsoftware.obdapp.util

import studio.etsoftware.obdapp.domain.model.CommandTelemetry
import studio.etsoftware.obdapp.domain.model.CycleTelemetry
import studio.etsoftware.obdapp.domain.model.MetricEmissionTelemetry
import studio.etsoftware.obdapp.domain.model.TelemetryEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogExportFormatter
    @Inject
    constructor() {
        private val exportDateFormat =
            ThreadLocal.withInitial {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            }

        private val fileNameDateFormat =
            ThreadLocal.withInitial {
                SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.getDefault())
            }

        private val eventTimeFormat =
            ThreadLocal.withInitial {
                SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            }

        fun buildExportText(
            logs: List<LogEntry>,
            telemetryEvents: List<TelemetryEvent> = emptyList(),
            exportedAtMillis: Long = System.currentTimeMillis(),
        ): String {
            val header =
                buildString {
                    appendLine("OBD Reader Debug Log Export")
                    appendLine("Exported at: ${formatExportDate(exportedAtMillis)}")
                    appendLine("Entries: ${logs.size}")
                    appendLine("Telemetry events: ${telemetryEvents.size}")
                    appendLine()
                }

            val entries =
                logs.joinToString(separator = "\n") { entry ->
                    "${entry.timestamp} [${entry.type.name}] ${sanitizeMessage(entry.message)}"
                }

            val telemetrySection =
                if (telemetryEvents.isEmpty()) {
                    ""
                } else {
                    buildString {
                        appendLine()
                        appendLine("Telemetry Export")
                        appendLine("Events: ${telemetryEvents.size}")
                        telemetryEvents.forEach { event ->
                            appendLine(formatTelemetryEvent(event))
                        }
                    }
                }

            return buildString {
                append(header)
                if (entries.isNotBlank()) {
                    append(entries)
                }
                append(telemetrySection)
            }
        }

        fun buildDefaultFileName(nowMillis: Long = System.currentTimeMillis()): String {
            return "obd-debug-log-${formatFileNameDate(nowMillis)}.txt"
        }

        private fun formatExportDate(timestampMillis: Long): String {
            return exportDateFormat.get()?.format(Date(timestampMillis)).orEmpty()
        }

        private fun formatFileNameDate(timestampMillis: Long): String {
            return fileNameDateFormat.get()?.format(Date(timestampMillis)).orEmpty()
        }

        private fun formatEventTime(timestampMillis: Long): String {
            return eventTimeFormat.get()?.format(Date(timestampMillis)).orEmpty()
        }

        private fun formatTelemetryEvent(event: TelemetryEvent): String {
            return when (event) {
                is CommandTelemetry ->
                    buildString {
                        append(formatEventTime(event.finishedAtMs))
                        append(" [TELEMETRY] CMD")
                        event.cycleId?.let { append(" cycle=$it") }
                        append(" ctx=${event.context.name.lowercase()}")
                        append(" pid=${event.rawPid}")
                        append(" name=${event.commandName}")
                        append(" dur=${event.durationMs}ms")
                        append(" ok=${event.success}")
                        event.errorType?.let { append(" err=$it") }
                        event.errorMessage?.let { append(" msg=${sanitizeMessage(it)}") }
                        event.valuePreview?.let { append(" value=${sanitizeMessage(it)}") }
                    }
                is CycleTelemetry ->
                    buildString {
                        append(formatEventTime(event.finishedAtMs))
                        append(" [TELEMETRY] CYCLE")
                        append(" id=${event.cycleId}")
                        append(" dur=${event.durationMs}ms")
                        append(" interval=${event.configuredIntervalMs}ms")
                        append(" commands=${event.commandCount}")
                        append(" ok=${event.successCount}")
                        append(" fail=${event.failureCount}")
                    }
                is MetricEmissionTelemetry ->
                    buildString {
                        append(formatEventTime(event.emittedAtMs))
                        append(" [TELEMETRY] EMIT")
                        event.cycleId?.let { append(" cycle=$it") }
                        append(" metric=${event.metricName}")
                        append(" value=${sanitizeMessage(event.value)}")
                    }
            }
        }

        private fun sanitizeMessage(message: String): String {
            return message.replace("\n", "\\n")
        }
    }
