package pl.twoja.apka.dji

import android.content.Context
import android.util.Log
import com.dji.wpmzsdk.common.data.HeightMode
import com.dji.wpmzsdk.manager.WPMZManager
import dji.sdk.wpmz.value.mission.Wayline
import dji.sdk.wpmz.value.mission.WaylineDroneInfo
import dji.sdk.wpmz.value.mission.WaylineDroneType
import dji.sdk.wpmz.value.mission.WaylineExecuteAltitudeMode
import dji.sdk.wpmz.value.mission.WaylineExecuteWaypoint
import dji.sdk.wpmz.value.mission.WaylineExitOnRCLostAction
import dji.sdk.wpmz.value.mission.WaylineExitOnRCLostBehavior
import dji.sdk.wpmz.value.mission.WaylineFinishedAction
import dji.sdk.wpmz.value.mission.WaylineFlyToWaylineMode
import dji.sdk.wpmz.value.mission.WaylineLocationCoordinate2D
import dji.sdk.wpmz.value.mission.WaylineMission
import dji.sdk.wpmz.value.mission.WaylineMissionConfig
import dji.sdk.wpmz.value.mission.WaylineWaypointTurnMode
import dji.sdk.wpmz.value.mission.WaylineWaypointTurnParam
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.waypoint3.WaypointMissionManager
import dji.v5.utils.common.FileUtils
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object KmzMissionHelper {

    private const val TAG = "KmzMissionHelper"
    private data class SimpleLatLng(
        val lat: Double,
        val lon: Double,
        val alt: Double? = null
    )

    fun generateKmzFromKmlAsset(
        context: Context,
        assetPath: String,
        defaultHeightMeters: Double = 10.0,
        defaultSpeed: Double = 3.0
    ): String {
        val points = parseCoordinatesFromKml(context, assetPath)
        if (points.size < 2) {
            throw IllegalStateException("W KML znaleziono mniej niż 2 punkty – misja nie ma sensu.")
        }
        val outDir = File(context.getExternalFilesDir(null), "kmz_out")
        if (!outDir.exists()) outDir.mkdirs()
        val outFile = File(outDir, "mission_from_kml.kmz")
        if (outFile.exists()) outFile.delete()
        val manager = WPMZManager.getInstance()
        manager.init(context.applicationContext)
        val mission = WaylineMission().apply {
            author = "TwojaApka"
            val now = System.currentTimeMillis().toDouble()
            createTime = now
            updateTime = now
        }
        val missionConfig = WaylineMissionConfig().apply {
            finishAction = WaylineFinishedAction.GO_HOME
            flyToWaylineMode = WaylineFlyToWaylineMode.SAFELY
            exitOnRCLostType = WaylineExitOnRCLostAction.GO_BACK
            exitOnRCLostBehavior = WaylineExitOnRCLostBehavior.EXCUTE_RC_LOST_ACTION
            securityTakeOffHeight = 20.0
            globalTransitionalSpeed = defaultSpeed
            globalRTHHeight = defaultHeightMeters
            droneInfo = WaylineDroneInfo(WaylineDroneType.WM260, 0)
        }
        val wayline = Wayline().apply {
            autoFlightSpeed = defaultSpeed
            mode = WaylineExecuteAltitudeMode.RELATIVE_TO_START_POINT
        }
        points.forEachIndexed { index: Int, p: SimpleLatLng ->
            val heightForThisWp: Double = p.alt ?: defaultHeightMeters
            val wp = WaylineExecuteWaypoint().apply {
                waypointIndex = index
                location = WaylineLocationCoordinate2D(p.lat, p.lon)
                executeHeight = heightForThisWp
                speed = defaultSpeed
                turnParam = WaylineWaypointTurnParam().apply {
                    turnMode = WaylineWaypointTurnMode.TO_POINT_AND_STOP_WITH_DISCONTINUITY_CURVATURE
                    turnDampingDistance = 0.0
                }
                useStraightLine = true
            }
            wayline.waypoints.add(wp)
        }
        Log.i(TAG, "Generuję KMZ do: ${outFile.absolutePath}")
        manager.generateKMZFile(
            outFile.absolutePath,
            mission,
            missionConfig,
            wayline
        )
        if (!outFile.exists()) {
            throw IllegalStateException("generateKMZFile nie wygenerowało pliku: ${outFile.absolutePath}")
        }
        Log.i(TAG, "KMZ wygenerowany OK: ${outFile.absolutePath}")
        return outFile.absolutePath
    }

    private fun parseCoordinatesFromKml(
        context: Context,
        assetPath: String
    ): List<SimpleLatLng> {
        context.assets.open(assetPath).use { input ->
            return parseCoordinatesFromStream(input)
        }
    }

    private fun parseCoordinatesFromStream(
        input: InputStream
    ): List<SimpleLatLng> {
        val text = input.bufferedReader().use { it.readText() }
        val startTag = "<coordinates>"
        val endTag = "</coordinates>"
        val start = text.indexOf(startTag)
        val end = text.indexOf(endTag)
        if (start == -1 || end == -1 || end <= start) {
            throw IllegalArgumentException("W pliku KML nie znaleziono sekcji <coordinates>")
        }
        val coordsBlock = text.substring(start + startTag.length, end).trim()
        val result = mutableListOf<SimpleLatLng>()
        coordsBlock.lines().forEach { raw: String ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val parts = line.split(',')
            if (parts.size < 2) return@forEach
            val lon = parts[0].trim().toDoubleOrNull()
            val lat = parts[1].trim().toDoubleOrNull()
            val alt = if (parts.size >= 3) parts[2].trim().toDoubleOrNull() else null
            if (lat != null && lon != null) {
                result.add(SimpleLatLng(lat = lat, lon = lon, alt = alt))
            }
        }
        if (result.isEmpty()) {
            throw IllegalArgumentException("Nie udało się sparsować żadnych współrzędnych z <coordinates>")
        }
        Log.i(TAG, "parseCoordinates: znaleziono ${result.size} punktów")
        return result
    }

    fun prepareMissionFromUserFile(
        context: Context,
        filePath: String,
        heightMode: HeightMode
    ): String {
        val inputFile = File(filePath)
        if (!inputFile.exists()) {
            throw IllegalArgumentException("Plik misji źródłowej nie istnieje: $filePath")
        }
        val lower = filePath.lowercase()
        if (lower.endsWith(".kmz")) {
            Log.i(TAG, "Używam istniejącego pliku KMZ: $filePath")
            return filePath
        }
        if (!lower.endsWith(".kml")) {
            throw IllegalArgumentException("Nieobsługiwany format pliku misji: $filePath")
        }
        val points = FileInputStream(inputFile).use { parseCoordinatesFromStream(it) }
        if (points.size < 2) {
            throw IllegalStateException("W KML znaleziono mniej niż 2 punkty – misja nie ma sensu.")
        }
        val outDir = File(context.getExternalFilesDir(null), "kmz_user_out")
        if (!outDir.exists()) outDir.mkdirs()
        val outFile = File(outDir, "${inputFile.nameWithoutExtension}.kmz")
        if (outFile.exists()) outFile.delete()
        val manager = WPMZManager.getInstance()
        manager.init(context.applicationContext)
        val mission = WaylineMission().apply {
            author = "TwojaApka"
            val now = System.currentTimeMillis().toDouble()
            createTime = now
            updateTime = now
        }
        val defaultHeightMeters = 10.0
        val defaultSpeed = 3.0
        val missionConfig = WaylineMissionConfig().apply {
            finishAction = WaylineFinishedAction.GO_HOME
            flyToWaylineMode = WaylineFlyToWaylineMode.SAFELY
            exitOnRCLostType = WaylineExitOnRCLostAction.GO_BACK
            exitOnRCLostBehavior = WaylineExitOnRCLostBehavior.EXCUTE_RC_LOST_ACTION
            securityTakeOffHeight = 20.0
            globalTransitionalSpeed = defaultSpeed
            globalRTHHeight = defaultHeightMeters
            droneInfo = WaylineDroneInfo(WaylineDroneType.WM260, 0)
        }
        val wayline = Wayline().apply {
            autoFlightSpeed = defaultSpeed
            mode = WaylineExecuteAltitudeMode.RELATIVE_TO_START_POINT
        }
        points.forEachIndexed { index: Int, p: SimpleLatLng ->
            val heightForThisWp = p.alt ?: defaultHeightMeters
            val wp = WaylineExecuteWaypoint().apply {
                waypointIndex = index
                location = WaylineLocationCoordinate2D(p.lat, p.lon)
                executeHeight = heightForThisWp
                speed = defaultSpeed
                turnParam = WaylineWaypointTurnParam().apply {
                    turnMode = WaylineWaypointTurnMode.TO_POINT_AND_STOP_WITH_DISCONTINUITY_CURVATURE
                    turnDampingDistance = 0.0
                }
                useStraightLine = true
            }
            wayline.waypoints.add(wp)
        }
        manager.generateKMZFile(
            outFile.absolutePath,
            mission,
            missionConfig,
            wayline
        )
        if (!outFile.exists()) {
            throw IllegalStateException("generateKMZFile nie wygenerowało pliku: ${outFile.absolutePath}")
        }
        Log.i(TAG, "KML użytkownika -> KMZ OK: ${outFile.absolutePath}")
        return outFile.absolutePath
    }

    fun uploadKmz(
        missionPath: String,
        callback: CommonCallbacks.CompletionCallbackWithProgress<Double>
    ) {
        Log.i(TAG, "Upload KMZ do drona: $missionPath")
        WaypointMissionManager.getInstance().pushKMZFileToAircraft(
            missionPath,
            object : CommonCallbacks.CompletionCallbackWithProgress<Double> {
                override fun onProgressUpdate(progress: Double) {
                    callback.onProgressUpdate(progress)
                }
                override fun onSuccess() {
                    Log.i(TAG, "Upload KMZ – sukces")
                    callback.onSuccess()
                }
                override fun onFailure(error: IDJIError) {
                    Log.e(TAG, "Upload KMZ – błąd: ${error.errorCode()} ${error.description()}")
                    callback.onFailure(error)
                }
            }
        )
    }

    fun startMissionFromPath(
        missionPath: String,
        callback: CommonCallbacks.CompletionCallback
    ) {
        val missionId = FileUtils.getFileName(missionPath, ".kmz")
        val waylineIDs = WaypointMissionManager.getInstance().getAvailableWaylineIDs(missionPath)
        Log.i(TAG, "Start misji z pliku: $missionPath")
        Log.i(TAG, "  missionId = $missionId, waylineIDs = $waylineIDs")
        WaypointMissionManager.getInstance().startMission(
            missionId,
            waylineIDs,
            object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    Log.i(TAG, "startMission – sukces")
                    callback.onSuccess()
                }

                override fun onFailure(error: IDJIError) {
                    Log.e(TAG, "startMission – błąd: ${error.errorCode()} ${error.description()}")
                    callback.onFailure(error)
                }
            }
        )
    }
}
