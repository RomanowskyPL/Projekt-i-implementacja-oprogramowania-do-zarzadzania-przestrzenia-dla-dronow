package pl.twoja.apka.dji

import java.io.File

object KmlRouteBuilder {

    fun buildKmlFile(
        outFile: File,
        name: String,
        points: List<RoutePoint>,
        includeLineString: Boolean = true
    ) {
        outFile.parentFile?.mkdirs()
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        sb.append("""<kml xmlns="http://www.opengis.net/kml/2.2">""").append("\n")
        sb.append("<Document>\n")
        sb.append("<name>").append(escape(name)).append("</name>\n")
        if (includeLineString) {
            sb.append("<Placemark>\n")
            sb.append("<name>").append(escape("${name}_LINE")).append("</name>\n")
            sb.append("<LineString>\n")
            sb.append("<tessellate>1</tessellate>\n")
            sb.append("<coordinates>\n")
            points.forEach { p ->
                sb.append("${p.lon},${p.lat},${p.heightM}\n")
            }
            sb.append("</coordinates>\n")
            sb.append("</LineString>\n")
            sb.append("</Placemark>\n")
        }
        points.forEachIndexed { idx, p ->
            sb.append("<Placemark>\n")
            sb.append("<name>").append(escape("${name}_P$idx")).append("</name>\n")
            sb.append("<Point>\n")
            sb.append("<coordinates>${p.lon},${p.lat},${p.heightM}</coordinates>\n")
            sb.append("</Point>\n")
            sb.append("</Placemark>\n")
        }
        sb.append("</Document>\n")
        sb.append("</kml>\n")
        outFile.writeText(sb.toString(), Charsets.UTF_8)
    }

    private fun escape(s: String): String {
        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
