package pl.twoja.apka.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.dji.wpmzsdk.common.data.HeightMode
import com.dji.wpmzsdk.manager.WPMZManager
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.KeyManager
import kotlinx.coroutines.launch
import pl.twoja.apka.R
import pl.twoja.apka.api.ApiClient
import pl.twoja.apka.dji.KmzMissionHelper
import java.io.File
import java.io.FileOutputStream

class KmzMissionActivity : ComponentActivity() {

    private lateinit var tvPath: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvTelemetry: TextView
    private lateinit var tvSatellites: TextView
    private lateinit var tvHomePoint: TextView
    private var currentMissionPath: String? = null
    private val keyManager: KeyManager = KeyManager.getInstance()
    private var telemetryHandler: Handler? = null
    private var telemetryRunnable: Runnable? = null
    private var telemetryRunning = false
    private var flightId: Int? = null
    private var finishSent = false

    companion object {
        private const val TAG = "KmzMissionActivity"
    }

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri: Uri? = result.data?.data
            if (uri == null) return@registerForActivityResult
            try {
                val localPath = copyUriToExternalFiles(uri)
                if (localPath == null) {
                    Toast.makeText(this, "Nie udało się skopiować pliku", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                val lower = localPath.lowercase()
                val finalKmz = if (lower.endsWith(".kml")) {
                    KmzMissionHelper.prepareMissionFromUserFile(
                        context = this,
                        filePath = localPath,
                        heightMode = HeightMode.RELATIVE
                    )
                } else {
                    localPath
                }
                currentMissionPath = finalKmz
                tvPath.text = "Ścieżka misji: $finalKmz"
                tvStatus.text = "Plik wybrany OK"
            } catch (e: Exception) {
                tvStatus.text = "Błąd wyboru: ${e.message}"
                Log.e(TAG, "pick error", e)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kmz_mission)
        WPMZManager.getInstance().init(applicationContext)
        tvPath = findViewById(R.id.tvKmzPath)
        tvStatus = findViewById(R.id.tvStatus)
        tvTelemetry = findViewById(R.id.tvTelemetry)
        tvSatellites = findViewById(R.id.tvSatellites)
        tvHomePoint = findViewById(R.id.tvHomePoint)
        val btnPrepare: Button = findViewById(R.id.btnPrepareMission)
        val btnUpload: Button = findViewById(R.id.btnUploadMission)
        val btnStart: Button = findViewById(R.id.btnStartMission)
        val btnBack: Button = findViewById(R.id.btnBack)
        val btnPreview: Button = findViewById(R.id.btnPreviewKmz)
        flightId = intent.getIntExtra("flight_id", -1).takeIf { it > 0 }
            ?: getSharedPreferences("app", MODE_PRIVATE).getInt("ACTIVE_FLIGHT_ID", -1).takeIf { it > 0 }
        if (flightId == null) {
            tvStatus.text = "Uwaga: brak flight_id — nie zaktualizuję statusu w DB."
        } else {
            tvStatus.text = "flight_id=$flightId"
        }
        btnPrepare.setOnClickListener {
            try {
                val missionPath = KmzMissionHelper.generateKmzFromKmlAsset(
                    context = this,
                    assetPath = "wpmz/trasa_testowa.kml",
                    defaultHeightMeters = 30.0,
                    defaultSpeed = 3.0
                )
                currentMissionPath = missionPath
                tvPath.text = "Ścieżka misji: $missionPath"
                tvStatus.text = "Misja przygotowana OK (KML → KMZ)"
            } catch (e: Exception) {
                tvStatus.text = "Błąd przygotowania misji: ${e.message}"
                Log.e(TAG, "prepare error", e)
            }
        }
        btnUpload.setOnClickListener {
            val path = currentMissionPath
            if (path.isNullOrEmpty()) {
                Toast.makeText(this, "Najpierw wybierz / przygotuj misję", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvStatus.text = "Upload misji..."
            KmzMissionHelper.uploadKmz(
                path,
                object : CommonCallbacks.CompletionCallbackWithProgress<Double> {
                    override fun onProgressUpdate(progress: Double) {
                        runOnUiThread {
                            tvStatus.text = "Upload misji: ${(progress * 100).toInt()}%"
                        }
                    }
                    override fun onSuccess() {
                        runOnUiThread {
                            tvStatus.text = "Upload misji – SUCCESS"
                            updateBasicFlightStatus()
                        }
                    }
                    override fun onFailure(error: IDJIError) {
                        runOnUiThread {
                            tvStatus.text =
                                "Upload misji – ERROR: ${error.errorCode()} ${error.description()}"
                        }
                    }
                }
            )
        }
        btnStart.setOnClickListener {
            val path = currentMissionPath
            if (path.isNullOrEmpty()) {
                Toast.makeText(this, "Najpierw przygotuj i wyślij misję", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvStatus.text = "Próba startu misji..."
            KmzMissionHelper.startMissionFromPath(
                path,
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        runOnUiThread {
                            tvStatus.text = tvStatus.text.toString() + "\nstartMission -> SUCCESS"
                            startTelemetryLogging()
                        }
                    }
                    override fun onFailure(error: IDJIError) {
                        runOnUiThread {
                            tvStatus.text =
                                "startMission – ERROR: ${error.errorCode()} ${error.description()}"
                        }
                    }
                }
            )
        }
        btnPreview.setOnClickListener {
            val intent = Intent(this, KmzPreviewActivity::class.java)
            currentMissionPath?.let { intent.putExtra("KMZ_PATH", it) }
            startActivity(intent)
        }
        btnBack.setOnClickListener { finish() }
    }

    private fun copyUriToExternalFiles(uri: Uri): String? {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return null
            val outDir = File(getExternalFilesDir(null), "missions_user")
            if (!outDir.exists()) outDir.mkdirs()
            val name = "selected_" + System.currentTimeMillis()
            val ext = contentResolver.getType(uri)?.let { mime ->
                when {
                    mime.contains("kml") -> ".kml"
                    mime.contains("kmz") -> ".kmz"
                    else -> ""
                }
            } ?: ""
            val guessedExt = run {
                val s = uri.toString().lowercase()
                when {
                    s.contains(".kml") -> ".kml"
                    s.contains(".kmz") -> ".kmz"
                    else -> ext
                }
            }
            val outFile = File(outDir, "$name$guessedExt")
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
            outFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "copyUriToExternalFiles error", e)
            null
        }
    }

    private fun updateBasicFlightStatus() {
        try {
            val satCount: Int = keyManager.getValue(
                KeyTools.createKey(FlightControllerKey.KeyGPSSatelliteCount),
                0
            )
            val home: LocationCoordinate2D = keyManager.getValue(
                KeyTools.createKey(FlightControllerKey.KeyHomeLocation),
                LocationCoordinate2D(0.0, 0.0)
            )
            tvSatellites.text = "Satelity: $satCount"
            tvHomePoint.text = if (home.latitude == 0.0 && home.longitude == 0.0) {
                "Home point: nie ustawiony"
            } else {
                "Home point: lat=%.7f, lon=%.7f".format(home.latitude, home.longitude)
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateBasicFlightStatus error", e)
        }
    }

    private fun startTelemetryLogging() {
        if (telemetryRunning) return
        telemetryRunning = true
        finishSent = false
        tvTelemetry.text = ""
        telemetryHandler = Handler(Looper.getMainLooper())
        val startTime = System.currentTimeMillis()
        telemetryRunnable = object : Runnable {
            override fun run() {
                try {
                    val isFlying: Boolean = keyManager.getValue(
                        KeyTools.createKey(FlightControllerKey.KeyIsFlying),
                        false
                    )
                    val loc3D: LocationCoordinate3D = keyManager.getValue(
                        KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D),
                        LocationCoordinate3D(0.0, 0.0, 0.0)
                    )
                    val t = (System.currentTimeMillis() - startTime) / 1000.0
                    val line = String.format(
                        "t=%.1fs  lat=%.6f  lon=%.6f  h=%.2fm  flying=%s",
                        t, loc3D.latitude, loc3D.longitude, loc3D.altitude, isFlying
                    )
                    val old = tvTelemetry.text.toString()
                    tvTelemetry.text = if (old.isEmpty()) line else "$old\n$line"
                    updateBasicFlightStatus()
                    if (!isFlying && t > 5.0) {
                        tvTelemetry.text = tvTelemetry.text.toString() +
                                "\n--- Lądowanie wykryte – zatrzymuję logowanie telemetrii ---"
                        if (!finishSent) {
                            finishSent = true
                            finishFlightInDb()
                        }
                        stopTelemetryLogging()
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "telemetry error", e)
                }
                telemetryHandler?.postDelayed(this, 3000L)
            }
        }
        telemetryHandler?.post(telemetryRunnable!!)
    }

    private fun finishFlightInDb() {
        val id = flightId
        if (id == null) {
            runOnUiThread {
                Toast.makeText(this, "Brak flight_id — nie mogę zakończyć lotu w DB.", Toast.LENGTH_LONG).show()
            }
            return
        }
        lifecycleScope.launch {
            try {
                val res = ApiClient.api.finishFlight(id)
                runOnUiThread {
                    val status = res["status"]?.toString() ?: "?"
                    val czasKonca = res["czas_konca"]?.toString() ?: "?"
                    Toast.makeText(this@KmzMissionActivity, "Lot zakończony: $status, czas_konca=$czasKonca", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "finishFlight error", e)
                runOnUiThread {
                    Toast.makeText(this@KmzMissionActivity, "Błąd aktualizacji lotu: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun stopTelemetryLogging() {
        telemetryRunning = false
        telemetryHandler?.removeCallbacksAndMessages(null)
        telemetryHandler = null
        telemetryRunnable = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTelemetryLogging()
    }
}
