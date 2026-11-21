package com.kirdev.pulsedeckpanel

import com.kirdev.pulsedeckpanel.core.utils.Format
import com.kirdev.pulsedeckpanel.data.model.PlayStatus
import com.kirdev.pulsedeckpanel.data.remote.Backoff
import com.kirdev.pulsedeckpanel.data.remote.Discovery
import com.kirdev.pulsedeckpanel.data.remote.PinnedTls
import com.kirdev.pulsedeckpanel.data.remote.Protocol
import com.kirdev.pulsedeckpanel.feature.connection.ConnectionViewModel
import com.kirdev.pulsedeckpanel.feature.monitoring.Names
import com.kirdev.pulsedeckpanel.data.local.DisplayPrefs
import com.kirdev.pulsedeckpanel.data.local.Limit
import com.kirdev.pulsedeckpanel.data.local.Tile
import com.kirdev.pulsedeckpanel.feature.monitoring.AlertMonitor
import com.kirdev.pulsedeckpanel.feature.monitoring.History
import com.kirdev.pulsedeckpanel.feature.monitoring.tileView
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

    @Test fun tilesShowDashesWithoutData() {
        for (tile in Tile.entries) assertEquals("—", tileView(tile, null, null, null).value)
        assertNull(tileView(Tile.CPU, null, null, null).fraction)
        assertFalse(tileView(Tile.NET, null, null, null).bar)
    }

    @Test fun hardwareNamesAndTiles() {
        assertEquals("Intel Core i5-13420H", Names.cpu("13th Gen Intel(R) Core(TM) i5-13420H"))
        assertEquals("AMD Ryzen 7 5800H with Radeon Graphics", Names.cpu("AMD Ryzen 7 5800H with Radeon Graphics"))
        assertEquals("RTX 3050", Names.gpu("NVIDIA GeForce RTX 3050 6GB Laptop GPU"))
        val m = metrics()
        assertEquals("RTX 3050 · 3.0 GB / 6.0 GB", tileView(Tile.GPU, m, null, null).caption)
        assertEquals("↓1.5 KB/s", tileView(Tile.NET, m, null, null).value)
        assertEquals("↑ —", tileView(Tile.NET, m, null, null).caption)
        assertEquals("D: Data", tileView(Tile.DISK, m, null, m.disks[1]).name)
        assertEquals("2", tileView(Tile.CORES, m, null, null).value)
        assertEquals("макс. 80%", tileView(Tile.CORES, m, null, null).caption)
        assertEquals("нет данных", tileView(Tile.VRAM, metrics(vram = false), null, null).caption)
        assertEquals("50%", tileView(Tile.VRAM, m, null, null).value)
    }

    @Test fun extraPcFields() {
        val m = metrics()
        assertEquals(3725.0, m.uptime!!, 0.1)
        assertEquals(54.0, m.battery!!.percent!!, 0.1)
        assertFalse(m.battery!!.plugged)
        assertEquals("1 ч 2 мин", Format.uptime(m.uptime))
        assertEquals("2 д 0 ч", Format.uptime(172_800.0))
    }

    @Test fun historyKeepsThreeMinutes() {
        var history = History()
        repeat(100) { history = history.plus(metrics()) }
        assertEquals(History.POINTS, history["cpu"].size)
        assertEquals(50.0, history["vram"].last()!!, 0.1)
    }

    @Test fun warningsNeedTenSecondsAndRearmBelowTheLimit() {
        val monitor = AlertMonitor()
        val prefs = DisplayPrefs()
        val hot = metrics(cpu = 95.0)
        assertTrue(monitor.check(hot, prefs, 2.0).isEmpty())
        repeat(3) { monitor.check(hot, prefs, 2.0) }
        val fired = monitor.check(hot, prefs, 2.0)
        assertEquals(listOf(Limit.CPU), fired.map { it.first })
        assertTrue(fired[0].second.contains("95%"))
        assertTrue(monitor.check(hot, prefs, 2.0).isEmpty())              // once, not every sample
        monitor.check(metrics(cpu = 88.0), prefs, 2.0)                    // within 5 points: still active
        assertTrue(Limit.CPU in monitor.activeLimits)
        monitor.check(metrics(cpu = 40.0), prefs, 2.0)
        assertFalse(Limit.CPU in monitor.activeLimits)
        assertTrue(AlertMonitor.tilesOf(setOf(Limit.CPU)).contains(Tile.CORES))
    }

    @Test fun prefsSurviveJsonAndFixBadValues() {
        val prefs = DisplayPrefs(accent = "green", layout = "music", tiles = listOf(Tile.VRAM, Tile.CPU),
            tileOrder = listOf(Tile.NET, Tile.CPU), limits = mapOf(Limit.CPU to 70), idle = "galaxy", themeByTime = true)
        val back = DisplayPrefs.fromJson(org.json.JSONObject(prefs.toJson().toString()))
        assertEquals("green", back.accent)
        assertEquals("music", back.layout)
        assertEquals(Tile.entries.size, back.tileOrder.size)               // missing tiles are added at the end
        assertEquals(listOf(Tile.NET, Tile.CPU), back.tileOrder.take(2))
        assertEquals(listOf(Tile.CPU, Tile.VRAM), back.shownTiles.filter { it == Tile.CPU || it == Tile.VRAM })
        assertEquals(70, back.limit(Limit.CPU))
        assertEquals(50, DisplayPrefs(limits = mapOf(Limit.CPU to 5)).cleaned().limit(Limit.CPU))
        assertEquals("LIGHT", back.themeAt(12 * 60))
        assertEquals("DARK", back.themeAt(22 * 60))
    }

    private fun metrics(cpu: Double = 5.0, vram: Boolean = true) = (Protocol.parse("""{"type":"metrics",
        "cpu":{"load":$cpu,"name":"X","cores":2,"per_core":[80,20]},
        "gpu":{"load":1,"name":"NVIDIA GeForce RTX 3050 6GB Laptop GPU","memory_total":6442450944,"memory_used":${if (vram) 3221225472 else null}},
        "ram":{"percent":50,"used":8,"total":16},"net":{"down":1536,"up":null},"uptime":3725,
        "battery":{"percent":54,"plugged":false},
        "disks":[{"name":"C:","label":"","total":100,"free":40},{"name":"D:","label":"Data","total":200,"free":100}]}""", 0)
        as Protocol.Message.MetricsUpdate).metrics
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

class UpdaterTest {
    private val updater = com.kirdev.pulsedeckpanel.data.update.Updater

    @Test fun versionsCompareByNumbers() {
        assertTrue(updater.isNewer("1.0.1", "1.0"))
        assertTrue(updater.isNewer("1.0.10", "1.0.9"))
        assertFalse(updater.isNewer("1.0", "1.0.0"))
        assertFalse(updater.isNewer("1.0", "1.1"))
    }

    @Test fun releaseNeedsAnApk() {
        val withApk = """{"tag_name":"v1.0.1","body":"Fixes","assets":[{"name":"PulseDeck-Panel-1.0.1.apk",
            "browser_download_url":"https://example/x.apk","size":10}]}"""
        val release = updater.parse(withApk)!!
        assertEquals("1.0.1", release.version)
        assertEquals("https://example/x.apk", release.apkUrl)
        assertNull(updater.parse("""{"tag_name":"v2","assets":[{"name":"notes.txt"}]}"""))
        assertNull(updater.parse("""{"tag_name":"v2","prerelease":true,"assets":[{"name":"a.apk"}]}"""))
    }
}
