package pl.twoja.apka.telemetry

import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object TelemetrySession {

    private var startTimeMs: Long = 0L
    private var active = false
    private val lines = CopyOnWriteArrayList<String>()

    fun startNewSession() {
        lines.clear()
        startTimeMs = System.currentTimeMillis()
        active = true
        addEvent("MISJA: START")
    }

    fun isActive(): Boolean = active

    fun stopSession() {
        active = false
    }

    fun addEvent(message: String) {
        val t = elapsedSeconds()
        val line = String.format(Locale("pl", "PL"), "t=%.1fs  [EVENT] %s", t, message)
        lines.add(line)
    }

    fun addTelemetry(lat: Double, lon: Double, alt: Double?, isFlying: Boolean) {
        val t = elapsedSeconds()
        val line = buildString {
            append(String.format(Locale("pl", "PL"), "t=%.1fs  lat=%.6f  lon=%.6f", t, lat, lon))
            if (alt != null) append(String.format(Locale("pl", "PL"), "  h=%.1fm", alt))
            append("  flying=")
            append(if (isFlying) "true" else "false")
        }
        lines.add(line)
    }

    fun getFullLogText(): String {
        return lines.joinToString(separator = "\n")
    }

    fun getLineCount(): Int = lines.size

    private fun elapsedSeconds(): Double {
        if (startTimeMs == 0L) return 0.0
        return (System.currentTimeMillis() - startTimeMs) / 1000.0
    }
}
