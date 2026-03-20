package com.eltonvs.obdapp.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class LogEntry(
    val timestamp: String,
    val type: LogType,
    val message: String,
)

enum class LogType {
    INFO,
    COMMAND,
    RESPONSE,
    ERROR,
    SUCCESS,
}

@Singleton
class LogManager
    @Inject
    constructor() {
        private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
        val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

        private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

        fun log(type: LogType, message: String) {
            val entry = LogEntry(
                timestamp = dateFormat.format(Date()),
                type = type,
                message = message,
            )
            _logs.value = (_logs.value + entry).takeLast(MAX_LOGS)
        }

        fun info(message: String) = log(LogType.INFO, message)
        fun command(message: String) = log(LogType.COMMAND, message)
        fun response(message: String) = log(LogType.RESPONSE, message)
        fun error(message: String) = log(LogType.ERROR, message)
        fun success(message: String) = log(LogType.SUCCESS, message)

        fun clear() {
            _logs.value = emptyList()
        }

        companion object {
            private const val MAX_LOGS = 500
        }
    }
