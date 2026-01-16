package pl.twoja.apka.dji

import android.util.Log
import java.io.File
import java.util.zip.ZipFile

object KmzValidator {

    private const val TAG = "KmzValidator"

    data class Result(
        val ok: Boolean,
        val message: String,
        val entries: List<String> = emptyList()
    )

    fun validate(kmz: File): Result {
        if (!kmz.exists() || kmz.length() <= 0L) {
            return Result(false, "KMZ nie istnieje lub pusty: ${kmz.absolutePath}")
        }
        return try {
            ZipFile(kmz).use { zip ->
                val names = zip.entries().toList().map { it.name }.sorted()
                val hasWpml = names.any { it.endsWith("waylines.wpml", ignoreCase = true) }

                Log.i(TAG, "KMZ entries (${names.size}): ${names.joinToString()}")

                if (!hasWpml) {
                    Result(false, "Brak waylines.wpml (dron zwykle odmówi startu)", names)
                } else {
                    Result(true, "OK (jest waylines.wpml)", names)
                }
            }
        } catch (e: Exception) {
            Result(false, "KMZ nie wygląda na ZIP: ${e.message}")
        }
    }

    private fun <T> java.util.Enumeration<T>.toList(): List<T> {
        val out = ArrayList<T>()
        while (hasMoreElements()) out.add(nextElement())
        return out
    }
}
