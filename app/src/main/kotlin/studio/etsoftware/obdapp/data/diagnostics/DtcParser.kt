package studio.etsoftware.obdapp.data.diagnostics

import javax.inject.Inject

class DtcParser
    @Inject
    constructor() {
        private val dtcRegex = Regex("[PCBU][0-3][0-9A-F]{3}")

        fun parse(rawValue: String): List<String> {
            if (rawValue.isBlank()) return emptyList()

            val normalized = rawValue.uppercase()
            if (normalized.contains("NO DATA") || normalized.contains("NODATA")) {
                return emptyList()
            }

            val directCodes =
                dtcRegex
                    .findAll(normalized)
                    .map { it.value }
                    .filterNot { it == "P0000" }
                    .distinct()
                    .toList()

            if (directCodes.isNotEmpty()) {
                return directCodes
            }

            val hexPayload = normalized.filter { it.isDigit() || it in 'A'..'F' }
            if (hexPayload.length < 4) {
                return emptyList()
            }

            return hexPayload
                .chunked(4)
                .mapNotNull { chunk ->
                    if (chunk.length < 4 || chunk == "0000") {
                        null
                    } else {
                        decodeFromHex(chunk)
                    }
                }.distinct()
        }

        fun descriptionFor(code: String): String = "Diagnostic Trouble Code $code"

        private fun decodeFromHex(rawCode: String): String? {
            val value = rawCode.toIntOrNull(16) ?: return null

            val system =
                when ((value and 0xC000) shr 14) {
                    0 -> 'P'
                    1 -> 'C'
                    2 -> 'B'
                    else -> 'U'
                }

            val code =
                buildString(5) {
                    append(system)
                    append((value and 0x3000) shr 12)
                    append(((value and 0x0F00) shr 8).toString(16).uppercase())
                    append(((value and 0x00F0) shr 4).toString(16).uppercase())
                    append((value and 0x000F).toString(16).uppercase())
                }

            return code.takeIf { it != "P0000" && dtcRegex.matches(it) }
        }
    }
