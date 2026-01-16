package pl.twoja.apka.dji

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import android.util.Xml
import java.io.File
import java.util.zip.ZipFile

object WpmlExtractor {
    private const val TAG = "WpmlExtractor"

    fun extractRoutePointsFromKmz(kmzFile: File): List<RoutePoint> {
        require(kmzFile.exists()) { "KMZ nie istnieje: ${kmzFile.absolutePath}" }
        val wpmlEntryNames = listOf(
            "wpmz/waylines.wpml",
            "WPMZ/waylines.wpml",
            "waylines.wpml"
        )
        ZipFile(kmzFile).use { zip ->
            val entry = wpmlEntryNames
                .mapNotNull { name -> zip.getEntry(name) }
                .firstOrNull()
                ?: error("Nie znaleziono waylines.wpml w KMZ. Szukano: $wpmlEntryNames")
            zip.getInputStream(entry).use { input ->
                return parseWaylinesWpml(input.readBytes().toString(Charsets.UTF_8))
            }
        }
    }

    private fun parseWaylinesWpml(xmlText: String): List<RoutePoint> {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setInput(xmlText.reader())
        val points = mutableListOf<RoutePoint>()
        var currentLonLat: Pair<Double, Double>? = null
        var currentHeight: Double? = null
        var currentSpeed: Double? = null
        fun flushIfPossible() {
            val ll = currentLonLat
            val h = currentHeight
            if (ll != null && h != null) {
                val lon = ll.first
                val lat = ll.second
                points.add(RoutePoint(lat = lat, lon = lon, heightM = h, speedMS = currentSpeed))
            }
            currentLonLat = null
            currentHeight = null
            currentSpeed = null
        }
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "Placemark" -> {
                        currentLonLat = null
                        currentHeight = null
                        currentSpeed = null
                    }
                    "coordinates" -> {
                        val txt = parser.nextText().trim()
                        val parts = txt.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        if (parts.size >= 2) {
                            val lon = parts[0].toDoubleOrNull()
                            val lat = parts[1].toDoubleOrNull()
                            if (lon != null && lat != null) {
                                currentLonLat = lon to lat
                            }
                        }
                    }
                    "executeHeight" -> {
                        val txt = parser.nextText().trim()
                        currentHeight = txt.toDoubleOrNull()
                    }
                    "waypointSpeed" -> {
                        val txt = parser.nextText().trim()
                        currentSpeed = txt.toDoubleOrNull()
                    }
                }
            } else if (parser.eventType == XmlPullParser.END_TAG) {
                if (parser.name == "Placemark") {
                    flushIfPossible()
                }
            }
            parser.next()
        }

        Log.i(TAG, "Wyciągnięto punktów: ${points.size}")
        return points
    }
}
