package com.kirdev.pulsedeckpanel.feature.connection

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kirdev.pulsedeckpanel.data.local.PairingStore
import com.kirdev.pulsedeckpanel.data.model.DiscoveredPc
import com.kirdev.pulsedeckpanel.data.model.PairedPc
import com.kirdev.pulsedeckpanel.data.remote.Discovery
import com.kirdev.pulsedeckpanel.data.remote.PairingClient
import com.kirdev.pulsedeckpanel.data.remote.PanelConnection
import com.kirdev.pulsedeckpanel.data.remote.PinnedTls
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * First pairing: find the PC, show its certificate fingerprint for comparison, send the
 * 6-digit code the PC displays, store the token. Nothing secret is sent before the user
 * has seen the fingerprint.
 */
class ConnectionViewModel(
    private val store: PairingStore,
    private val client: PairingClient,
    private val connection: PanelConnection,
) : ViewModel() {

    sealed interface Step {
        data object Searching : Step
        data class Found(val pcs: List<DiscoveredPc>) : Step
        data object Manual : Step
        data class Probing(val host: String) : Step
        data class EnterCode(val name: String, val host: String, val port: Int, val fingerprint: String,
                             val codeShown: Boolean, val error: String? = null, val sending: Boolean = false) : Step
        data object Paired : Step
        data class Failed(val message: String) : Step
    }

    private val _step = MutableStateFlow<Step>(Step.Searching)     // the screen calls search() on every visit
    val step: StateFlow<Step> = _step.asStateFlow()
    private var job: Job? = null

    fun search() {
        job?.cancel()
        _step.value = Step.Searching
        job = viewModelScope.launch {
            val pcs = runCatching { Discovery.find() }.getOrDefault(emptyList())
            _step.value = if (pcs.isEmpty()) Step.Failed("ПК не найден. Убедитесь, что PulseDeck запущен, а телефон в той же сети Wi-Fi.")
                          else Step.Found(pcs)
        }
    }

    fun showError(message: String) {
        _step.value = Step.Failed(message)
    }

    fun cancel() {
        job?.cancel()
        _step.value = Step.Found(emptyList())
    }

    fun manual() {
        job?.cancel()
        _step.value = Step.Manual
    }

    fun choose(pc: DiscoveredPc) {
        val port = pc.tlsPort ?: return
        probe(pc.host, port, pc.name)
    }

    /** «192.168.1.5» or «192.168.1.5:8767». */
    fun connectManual(input: String) {
        val text = input.trim()
        val host = text.substringBeforeLast(':').takeIf { ':' in text && text.count { it == ':' } == 1 } ?: text
        val port = text.substringAfterLast(':', "").toIntOrNull()?.takeIf { ':' in text && text.count { it == ':' } == 1 }
            ?: DEFAULT_TLS_PORT
        if (!isPrivateAddress(host)) {
            _step.value = Step.Failed("Введите адрес ПК в локальной сети, например 192.168.1.5.")
            return
        }
        probe(host, port, host)
    }

    private fun probe(host: String, port: Int, name: String) {
        job?.cancel()
        _step.value = Step.Probing(host)
        job = viewModelScope.launch {
            _step.value = try {
                val probe = client.probe(host, port)
                Step.EnterCode(name, host, port, probe.fingerprint, probe.codeShown)
            } catch (e: Exception) {
                Step.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Не удалось связаться с ПК $host:$port.")
            }
        }
    }

    /** QR code from the PC: addresses, port, full fingerprint and code. The certificate must match. */
    fun pairFromQr(text: String) {
        val qr = PairingQr.parse(text)
        if (qr == null) {
            _step.value = Step.Failed("Это не QR-код PulseDeck. Откройте на ПК «Телефон» → «Подключить по коду».")
            return
        }
        job?.cancel()
        _step.value = Step.Probing(qr.hosts.firstOrNull() ?: "ПК")
        job = viewModelScope.launch {
            var lastError = "ПК не отвечает ни по одному адресу из QR-кода. Телефон в той же сети?"
            for (host in qr.hosts.filter(::isPrivateAddress)) {
                val probe = runCatching { client.probe(host, qr.port) }.getOrNull() ?: continue
                if (!probe.fingerprint.replace(":", "").equals(qr.fingerprint, ignoreCase = true)) {
                    lastError = "Сертификат ПК не совпадает с QR-кодом. Подключение остановлено."
                    continue
                }
                try {
                    val (token, pcName) = client.pair(host, qr.port, probe.fingerprint, qr.code, deviceName())
                    store.save(PairedPc.of(pcName.ifBlank { qr.name }, host, qr.port, probe.fingerprint), token)
                    connection.restart()
                    _step.value = Step.Paired
                    return@launch
                } catch (e: Exception) {
                    lastError = e.message ?: "ПК отклонил подключение."
                    break
                }
            }
            _step.value = Step.Failed(lastError)
        }
    }

    fun submit(code: String) {
        val current = _step.value as? Step.EnterCode ?: return
        val digits = code.filter(Char::isDigit)
        if (digits.length != 6) {
            _step.value = current.copy(error = "Код — 6 цифр с экрана «Телефон» на ПК.")
            return
        }
        _step.value = current.copy(sending = true, error = null)
        job = viewModelScope.launch {
            try {
                val (token, pcName) = client.pair(current.host, current.port, current.fingerprint, digits, deviceName())
                store.save(PairedPc.of(pcName.ifBlank { current.name }, current.host, current.port, current.fingerprint), token)
                connection.restart()
                _step.value = Step.Paired
            } catch (e: Exception) {
                _step.value = current.copy(sending = false, error = e.message ?: "Не удалось подключиться.")
            }
        }
    }

    companion object {
        const val DEFAULT_TLS_PORT = 8767

        fun shortFingerprint(fingerprint: String) = PinnedTls.short(fingerprint)

        fun deviceName() = "PulseDeck Panel (${Build.MODEL})".take(40)

        /** The PC only answers its LAN; refuse anything else before even trying. */
        fun isPrivateAddress(host: String): Boolean {
            val parts = host.split('.').mapNotNull { it.toIntOrNull() }
            if (parts.size != 4 || parts.any { it !in 0..255 }) return false
            return parts[0] == 10 || (parts[0] == 172 && parts[1] in 16..31) || (parts[0] == 192 && parts[1] == 168) ||
                (parts[0] == 169 && parts[1] == 254)
        }
    }
}
