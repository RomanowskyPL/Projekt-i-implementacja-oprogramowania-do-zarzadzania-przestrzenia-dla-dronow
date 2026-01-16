package pl.twoja.apka.dji

import android.content.Context
import java.io.File

object MissionAssets {

    fun copyAssetToMissions(context: Context, assetName: String): File {
        val outDir = context.getExternalFilesDir("missions") ?: context.filesDir
        outDir.mkdirs()

        val outFile = File(outDir, assetName)
        context.assets.open(assetName).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }
}
