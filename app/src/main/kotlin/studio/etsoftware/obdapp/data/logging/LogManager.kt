package studio.etsoftware.obdapp.data.logging

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import studio.etsoftware.obdapp.domain.model.DebugLogEntry
import studio.etsoftware.obdapp.domain.model.DebugLogType
import javax.inject.Inject
import javax.inject.Singleton

typealias LogEntry = DebugLogEntry

typealias LogType = DebugLogType

@Singleton
class LogManager
    @Inject
    constructor() {
        private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
        val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

        private val dateFormat =
            ThreadLocal.withInitial {
                SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            }

        fun log(
            type: LogType,
            message: String,
        ) {
            val entry =
                LogEntry(
                    timestamp = dateFormat.get()?.format(Date()).orEmpty(),
                    type = type,
                    message = message,
                )
            _logs.update { current ->
                (current + entry).takeLast(MAX_LOGS)
            }
        }

        fun info(message: String) = log(LogType.INFO, message)

        fun command(message: String) = log(LogType.COMMAND, message)

        fun response(message: String) = log(LogType.RESPONSE, message)

        fun error(message: String) = log(LogType.ERROR, message)

        fun success(message: String) = log(LogType.SUCCESS, message)

        fun telemetry(message: String) = log(LogType.TELEMETRY, message)

        fun clear() {
            _logs.update { emptyList() }
        }

        companion object {
            private const val MAX_LOGS = 500
        }
    }
