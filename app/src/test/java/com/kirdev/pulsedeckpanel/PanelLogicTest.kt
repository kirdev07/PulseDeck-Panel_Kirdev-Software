package com.kirdev.pulsedeckpanel

import com.kirdev.pulsedeckpanel.core.utils.Format
import com.kirdev.pulsedeckpanel.data.model.PlayStatus
import com.kirdev.pulsedeckpanel.data.remote.Backoff
import com.kirdev.pulsedeckpanel.data.remote.Discovery
import com.kirdev.pulsedeckpanel.data.remote.PinnedTls
import com.kirdev.pulsedeckpanel.data.remote.Protocol
import com.kirdev.pulsedeckpanel.feature.connection.ConnectionViewModel
import com.kirdev.pulsedeckpanel.feature.monitoring.Names
import com.kirdev.pulsedeckpanel.feature.monitoring.barState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    @Test fun hello() {
        val message = Protocol.parse("""{"type":"hello","protocol":1,"name":"DESKTOP-1"}""", 0)
        assertEquals(Protocol.Message.Hello(1, "DESKTOP-1"), message)
    }

    @Test fun metricsKeepUnknownValuesNull() {
        val text = """{"type":"metrics","cpu":{"load":24.4,"temp":null},"gpu":{"load":null,"temp":null,"name":null},
            "ram":{"percent":46.0,"used":8,"total":16},
            "disks":[{"name":"C:","label":"Windows","total":100,"free":40},{"name":"E:","label":"","total":0,"free":0}]}"""
        val metrics = (Protocol.parse(text, 0) as Protocol.Message.MetricsUpdate).metrics
        assertEquals(24.4, metrics.cpu.load!!, 0.001)
        assertNull(metrics.cpu.temp)
        assertNull(metrics.gpu.load)
        assertNull(metrics.gpuName)
        assertEquals(1, metrics.disks.size)                 // empty volume dropped
        assertEquals(0.6f, metrics.disks[0].usedFraction, 0.001f)
    }

    @Test fun mediaSessionAndNone() {
        val playing = Protocol.parse("""{"type":"media","session":{"title":"After Dark","artist":"Mr.Kitty",
            "status":"playing","position":42,"duration":208,"cover":"k","can_prev":true,"can_next":false}}""", 1000)
        val session = (playing as Protocol.Message.MediaUpdate).session!!
        assertEquals(PlayStatus.PLAYING, session.status)
        assertFalse(session.canNext)
        assertNull((Protocol.parse("""{"type":"media","session":null}""", 0) as Protocol.Message.MediaUpdate).session)
    }

    @Test fun unknownAndBrokenMessagesAreIgnored() {
        assertEquals(Protocol.Message.Unknown, Protocol.parse("""{"type":"future"}""", 0))
        assertEquals(Protocol.Message.Unknown, Protocol.parse("not json", 0))
    }

    @Test fun onlyPlaybackCommands() {
        val json = org.json.JSONObject(Protocol.command("toggle"))
        assertEquals("command", json.getString("type"))
        assertEquals("toggle", json.getString("action"))
        try {
            Protocol.command("shutdown")
            throw AssertionError("shutdown must be refused")
        } catch (_: IllegalArgumentException) {
        }
    }
}

class PlaybackTest {
    private fun session(status: String) = (Protocol.parse(
        """{"type":"media","session":{"title":"A","status":"$status","position":10,"duration":12}}""", 1_000)
        as Protocol.Message.MediaUpdate).session!!

    @Test fun positionMovesOnlyWhilePlayingAndStopsAtTheEnd() {
        assertEquals(11.5, session("playing").positionAt(2_500), 0.001)
        assertEquals(12.0, session("playing").positionAt(60_000), 0.001)
        assertEquals(10.0, session("paused").positionAt(60_000), 0.001)
    }
}

class FormatTest {
    @Test fun values() {
        assertEquals("—", Format.percent(null))
        assertEquals("24%", Format.percent(24.4))
        assertEquals("312 GB", Format.bytes(312L * 1024 * 1024 * 1024))
        assertEquals("1.4 TB", Format.bytes(1_400L * 1024 * 1024 * 1024))       // 1.37 TiB
        assertEquals("3:28", Format.time(208.0))
        assertEquals("1:02:05", Format.time(3725.0))
    }

    @Test fun barShowsRealVolumesAndDashesWithoutData() {
        assertEquals(listOf("CPU", "GPU", "RAM", "Сеть"), barState(null).cells.map { it.name })
        assertTrue(barState(null).cells.all { it.value == "—" && it.fraction == null })
        assertTrue(barState(null).disks.isEmpty())
    }

    @Test fun hardwareNamesAndSeveralDisks() {
        assertEquals("Intel Core i5-13420H", Names.cpu("13th Gen Intel(R) Core(TM) i5-13420H"))
        assertEquals("AMD Ryzen 7 5800H with Radeon Graphics", Names.cpu("AMD Ryzen 7 5800H with Radeon Graphics"))
        assertEquals("RTX 3050", Names.gpu("NVIDIA GeForce RTX 3050 6GB Laptop GPU"))
        val text = """{"type":"metrics","cpu":{"load":5,"name":"X"},
            "gpu":{"load":1,"name":"NVIDIA GeForce RTX 3050 6GB Laptop GPU","memory_total":6442450944,"memory_used":0},
            "ram":{"percent":50,"used":8,"total":16},"net":{"down":1536,"up":null},
            "disks":[{"name":"C:","label":"","total":100,"free":40},{"name":"D:","label":"Data","total":200,"free":100}]}"""
        val bar = barState((Protocol.parse(text, 0) as Protocol.Message.MetricsUpdate).metrics)
        assertEquals("RTX 3050 · 6.0 GB", bar.cells[1].caption)
        assertEquals("↓1.5 KB/s", bar.cells[3].value)
        assertEquals("↑ —", bar.cells[3].caption)
        assertEquals(listOf("C:", "D: Data"), bar.disks.map { it.name })
    }
}

class ConnectionLogicTest {
    @Test fun backoffGrowsAndCaps() {
        assertEquals(listOf(1, 2, 5, 10, 15, 30, 30), (1..7).map(Backoff::seconds))
    }

    @Test fun onlyLanAddressesForManualEntry() {
        assertTrue(ConnectionViewModel.isPrivateAddress("192.168.10.5"))
        assertTrue(ConnectionViewModel.isPrivateAddress("10.0.0.2"))
        assertTrue(ConnectionViewModel.isPrivateAddress("172.20.1.1"))
        assertFalse(ConnectionViewModel.isPrivateAddress("8.8.8.8"))
        assertFalse(ConnectionViewModel.isPrivateAddress("pc.example.com"))
        assertFalse(ConnectionViewModel.isPrivateAddress("192.168.1"))
    }

    @Test fun shortFingerprintMatchesThePcFormat() {
        val full = "77:7B:B6:E3:66:32:B5:A1:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77"
        assertEquals("777B B6E3 6632 B5A1", PinnedTls.short(full))
    }

    @Test fun discoveryReplies() {
        val pc = Discovery.parse("""{"app":"PulsePC","name":"DESKTOP-1","port":8765,"tls_port":8767,"api":[0,1]}""", "192.168.1.5")!!
        assertEquals(8767, pc.tlsPort)
        assertTrue(pc.supported)
        assertFalse(Discovery.parse("""{"app":"PulsePC","name":"OLD","port":8765}""", "192.168.1.6")!!.supported)
        assertNull(Discovery.parse("""{"app":"Other"}""", "192.168.1.7"))
    }
}

class PairingQrTest {
    private val fp = "a".repeat(64)

    @Test fun parsesPairingLink() {
        val qr = com.kirdev.pulsedeckpanel.feature.connection.PairingQr.parse(
            "pulsedeck://pair?v=1&name=DESKTOP%201&hosts=192.168.1.5,10.0.0.2&port=8767&fp=$fp&code=123456")!!
        assertEquals("DESKTOP 1", qr.name)
        assertEquals(listOf("192.168.1.5", "10.0.0.2"), qr.hosts)
        assertEquals("123456", qr.code)
    }

    @Test fun rejectsForeignOrBrokenLinks() {
        val parse = com.kirdev.pulsedeckpanel.feature.connection.PairingQr::parse
        assertEquals(null, parse("https://example.com/pair?fp=$fp&code=123456"))
        assertEquals(null, parse("pulsedeck://pair?fp=short&code=123456"))
        assertEquals(null, parse("pulsedeck://pair?fp=$fp&code=12ab56"))
        assertEquals(null, parse(null))
    }
}
