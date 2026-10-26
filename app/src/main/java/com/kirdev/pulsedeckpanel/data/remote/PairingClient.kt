package com.kirdev.pulsedeckpanel.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/** One-time pairing with a PC by the 6-digit code it shows (API v1). */
class PairingClient {
    data class Probe(val fingerprint: String, val codeShown: Boolean)

    class PairingException(message: String) : IOException(message)

    /** Contacts the PC without sending anything secret and records its certificate. */
    suspend fun probe(host: String, port: Int): Probe = withContext(Dispatchers.IO) {
        val trust = PinnedTls.PinTrustManager(pin = null)
        val request = Request.Builder().url("https://${hostPart(host)}:$port/api/v1/info").build()
        PinnedTls.client(trust).newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw PairingException("ПК ответил ошибкой ${response.code}.")
            val info = JSONObject(response.body?.string().orEmpty())
            if (info.optString("app") != "PulsePC" || info.optInt("api") < 1) {
                throw PairingException("Это не PulseDeck или версия без защищённого подключения.")
            }
            Probe(trust.seen ?: throw PairingException("Не удалось получить сертификат ПК."),
                  info.optBoolean("pairing"))
        }
    }

    /** Sends the code over TLS pinned to [fingerprint]; returns this phone's token. */
    suspend fun pair(host: String, port: Int, fingerprint: String, code: String, device: String): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val trust = PinnedTls.PinTrustManager(pin = fingerprint)
            val body = JSONObject().put("device", device).put("code", code).toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("https://${hostPart(host)}:$port/api/v1/pair").post(body).build()
            PinnedTls.client(trust).newCall(request).execute().use { response ->
                val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrDefault(JSONObject())
                if (!response.isSuccessful) {
                    throw PairingException(json.optString("error").ifBlank { "ПК отклонил подключение (${response.code})." })
                }
                val token = json.optString("token")
                if (token.isBlank()) throw PairingException("ПК не выдал ключ доступа.")
                token to json.optString("name")
            }
        }

    companion object {
        fun hostPart(host: String) = if (':' in host) "[$host]" else host     // IPv6 literal
    }
}
