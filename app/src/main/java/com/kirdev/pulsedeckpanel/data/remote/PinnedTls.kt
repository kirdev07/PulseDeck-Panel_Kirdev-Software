package com.kirdev.pulsedeckpanel.data.remote

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * TLS to a PulseDeck PC. Its certificate is self-signed, so no CA can vouch for it; instead
 * the SHA-256 fingerprint is pinned at pairing (after the user compared it with the PC
 * screen) and every later connection must present exactly that certificate.
 */
object PinnedTls {
    fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString(":") { "%02X".format(it) }

    /** First 8 bytes in groups of four hex digits, as the PC shows it: «CB9D BB10 52EF 2C38». */
    fun short(fingerprint: String): String =
        fingerprint.replace(":", "").take(16).chunked(4).joinToString(" ")

    /**
     * Trusts only [pin]. With [pin] == null (the very first contact during pairing) any
     * certificate is accepted but recorded in [seen], so the user can compare it before
     * anything secret is sent.
     */
    class PinTrustManager(private val pin: String?) : X509TrustManager {
        @Volatile var seen: String? = null
            private set

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("Сервер не прислал сертификат.")
            val actual = fingerprint(leaf)
            seen = actual
            if (pin != null && !actual.equals(pin, ignoreCase = true)) {
                throw CertificateMismatch(actual)
            }
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            throw CertificateException("Client certificates are not used.")

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    class CertificateMismatch(val actual: String) : CertificateException("Сертификат ПК изменился.")

    fun client(trust: PinTrustManager, pingSeconds: Long = 0): OkHttpClient {
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
        return OkHttpClient.Builder()
            .sslSocketFactory(context.socketFactory, trust)
            // The PC is addressed by IP and identified by the pinned certificate, not by host name.
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(pingSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
