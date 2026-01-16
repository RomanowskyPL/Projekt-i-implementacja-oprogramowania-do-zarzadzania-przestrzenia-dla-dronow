package pl.twoja.apka.dji

import android.os.Handler
import android.os.Looper
import android.util.Log
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.waypoint3.WaypointMissionManager
import java.io.File

object DjiWaylineHelper {

    private const val TAG = "DjiWaylineHelper"
    private val mainHandler = Handler(Looper.getMainLooper())
    fun startMissionFromKmzFile(
        kmzFile: File,
        cb: (ok: Boolean, msg: String) -> Unit
    ) {
        if (!kmzFile.exists() || kmzFile.length() <= 0L) {
            cb(false, "KMZ nie istnieje lub pusty: ${kmzFile.absolutePath}")
            return
        }
        val mgr = WaypointMissionManager.getInstance()
        val missionFileName = kmzFile.name
        Log.i(TAG, "pushKMZFileToAircraft: path=${kmzFile.absolutePath} name=$missionFileName size=${kmzFile.length()}")
        mgr.pushKMZFileToAircraft(
            kmzFile.absolutePath,
            object : CommonCallbacks.CompletionCallbackWithProgress<Double> {
                override fun onProgressUpdate(progress: Double) {
                    val pct = (progress * 100.0).toInt().coerceIn(0, 100)
                    Log.i(TAG, "Upload progress: $pct%")
                }
                override fun onSuccess() {
                    Log.i(TAG, "Upload OK -> startMission($missionFileName)")
                    startMissionWithRetry(
                        mgr = mgr,
                        missionFileName = missionFileName,
                        maxAttempts = 20,
                        delayMs = 2000L,
                        cb = cb
                    )
                }
                override fun onFailure(error: IDJIError) {
                    val msg = "Upload FAIL: ${error.description()}"
                    Log.e(TAG, msg)
                    cb(false, msg)
                }
            }
        )
    }

    private fun startMissionWithRetry(
        mgr: WaypointMissionManager,
        missionFileName: String,
        maxAttempts: Int,
        delayMs: Long,
        cb: (ok: Boolean, msg: String) -> Unit,
        attempt: Int = 1
    ) {
        mgr.startMission(
            missionFileName,
            object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    val msg = "startMission OK ($missionFileName)"
                    Log.i(TAG, msg)
                    cb(true, msg)
                }
                override fun onFailure(error: IDJIError) {
                    val desc = error.description()
                    Log.w(TAG, "startMission FAIL attempt=$attempt/$maxAttempts -> $desc")
                    val looksLikeHomePoint = desc.contains("home point", ignoreCase = true) ||
                            desc.contains("not updated", ignoreCase = true) ||
                            desc.contains("Home", ignoreCase = true)
                    if (looksLikeHomePoint && attempt < maxAttempts) {
                        mainHandler.postDelayed({
                            startMissionWithRetry(
                                mgr = mgr,
                                missionFileName = missionFileName,
                                maxAttempts = maxAttempts,
                                delayMs = delayMs,
                                cb = cb,
                                attempt = attempt + 1
                            )
                        }, delayMs)
                    } else {
                        cb(false, "startMission FAIL: $desc")
                    }
                }
            }
        )
    }

    fun uploadAndStartKmz(
        kmzFile: File,
        cb: (ok: Boolean, msg: String) -> Unit
    ) {
        startMissionFromKmzFile(kmzFile, cb)
    }
}
