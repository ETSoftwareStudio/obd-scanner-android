package com.eltonvs.obdapp.util

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

        fun buildExportText(
            logs: List<LogEntry>,
            exportedAtMillis: Long = System.currentTimeMillis(),
        ): String {
            val header =
                buildString {
                    appendLine("OBD Reader Debug Log Export")
                    appendLine("Exported at: ${formatExportDate(exportedAtMillis)}")
                    appendLine("Entries: ${logs.size}")
                    appendLine()
                }

            val entries =
                logs.joinToString(separator = "\n") { entry ->
                    "${entry.timestamp} [${entry.type.name}] ${sanitizeMessage(entry.message)}"
                }

            return if (entries.isBlank()) {
                header
            } else {
                "$header$entries"
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

        private fun sanitizeMessage(message: String): String {
            return message.replace("\n", "\\n")
        }
    }
