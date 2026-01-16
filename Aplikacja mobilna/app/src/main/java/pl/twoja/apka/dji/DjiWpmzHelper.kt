package pl.twoja.apka.dji

import android.util.Log
import com.dji.wpmzsdk.common.data.HeightMode
import com.dji.wpmzsdk.interfaces.IWPMZManager
import com.dji.wpmzsdk.manager.WPMZManager
import java.io.File

object DjiWpmzHelper {
    private const val TAG = "DjiWpmzHelper"
    private val wpmz: IWPMZManager by lazy { WPMZManager.getInstance() }

    fun convertKmlToKmz(
        kmlFile: File,
        outKmzFile: File,
        heightMode: HeightMode = HeightMode.EGM96
    ): Result<File> = runCatching {
        require(kmlFile.exists()) { "KML nie istnieje: ${kmlFile.absolutePath}" }
        outKmzFile.parentFile?.mkdirs()
        if (outKmzFile.exists()) outKmzFile.delete()
        val ok = wpmz.transKMLtoKMZ(kmlFile.absolutePath, outKmzFile.absolutePath, heightMode)
        check(ok) { "transKMLtoKMZ zwrócił false – konwersja nieudana" }
        Log.i(TAG, "KML->KMZ OK: ${outKmzFile.absolutePath}")
        outKmzFile
    }

    fun validateKmzWithDji(kmzFile: File): Result<String> = runCatching {
        require(kmzFile.exists()) { "KMZ nie istnieje: ${kmzFile.absolutePath}" }
        val err = wpmz.checkValidation(kmzFile.absolutePath)
        val asText = err.toString()
        Log.i(TAG, "checkValidation: $asText")
        asText
    }

    fun uploadAndStartKmz(
        kmzFile: File,
        cb: (ok: Boolean, msg: String) -> Unit
    ) {
        DjiWaylineHelper.uploadAndStartKmz(kmzFile, cb)
    }
}
