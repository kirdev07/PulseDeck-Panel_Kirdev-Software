package com.kirdev.pulsedeckpanel.data.remote

import com.kirdev.pulsedeckpanel.data.model.DiscoveredPc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/** Finds PulseDeck on the local network: UDP broadcast «PULSEPC?» to port 8766 (same as Pulse Mobile). */
object Discovery {
    const val PORT = 8766
    private val REQUEST = "PULSEPC?".toByteArray()

    suspend fun find(timeoutMs: Int = 2500): List<DiscoveredPc> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, DiscoveredPc>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 300
            for (address in broadcastAddresses()) {
                runCatching { socket.send(DatagramPacket(REQUEST, REQUEST.size, address, PORT)) }
            }
            val buffer = ByteArray(1024)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                parse(String(packet.data, 0, packet.length), packet.address.hostAddress ?: continue)
                    ?.let { found[it.host] = it }
            }
        }
        found.values.sortedBy { it.name.lowercase() }
    }

    fun parse(reply: String, host: String): DiscoveredPc? = runCatching {
        val json = JSONObject(reply)
        if (json.optString("app") != "PulsePC") return null     // protocol identifier, kept from Pulse-PC
        DiscoveredPc(
            name = json.optString("name").ifBlank { host },
            host = host,
            tlsPort = json.optInt("tls_port", 0).takeIf { it > 0 },
        )
    }.getOrNull()

    /** Subnet broadcast of every active interface plus the global one (some routers drop it). */
    private fun broadcastAddresses(): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNullTo(result) { it.broadcast }
        }
        result += InetAddress.getByName("255.255.255.255")
        return result.distinct()
    }
}
