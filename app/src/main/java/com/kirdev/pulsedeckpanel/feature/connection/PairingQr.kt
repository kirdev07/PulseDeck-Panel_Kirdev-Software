package com.kirdev.pulsedeckpanel.feature.connection

import java.net.URI
import java.net.URLDecoder

/** pulsedeck://pair?v=1&name=…&hosts=ip,ip&port=8767&fp=<SHA-256 hex>&code=123456 (see docs/PANEL_PROTOCOL.md). */
data class PairingQr(val name: String, val hosts: List<String>, val port: Int, val fingerprint: String, val code: String) {
    companion object {
        fun parse(text: String?): PairingQr? = runCatching {
            val uri = URI(text?.trim() ?: return null)
            if (uri.scheme != "pulsedeck" || uri.host != "pair") return null
            val query = uri.rawQuery.orEmpty().split('&').mapNotNull {
                val parts = it.split('=', limit = 2)
                if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else null
            }.toMap()
            val fingerprint = query["fp"].orEmpty()
            val code = query["code"].orEmpty()
            if (fingerprint.length != 64 || !fingerprint.all { it.isLetterOrDigit() } || !code.matches(Regex("""\d{6}"""))) return null
            PairingQr(query["name"].orEmpty(), query["hosts"].orEmpty().split(',').filter { it.isNotBlank() },
                query["port"]?.toIntOrNull() ?: 8767, fingerprint, code)
        }.getOrNull()
    }
}
