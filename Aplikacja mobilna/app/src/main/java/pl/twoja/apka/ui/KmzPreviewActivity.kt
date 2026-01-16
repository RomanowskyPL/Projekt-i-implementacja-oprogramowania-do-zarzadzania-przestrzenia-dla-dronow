package pl.twoja.apka.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.dji.wpmzsdk.common.data.HeightMode
import com.dji.wpmzsdk.manager.WPMZManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.waypoint3.WaypointMissionManager
import dji.v5.utils.common.FileUtils
import pl.twoja.apka.R
import pl.twoja.apka.dji.KmzMissionHelper
import java.io.File
import java.io.FileOutputStream

class KmzPreviewActivity : ComponentActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null
    private lateinit var tvPath: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnChoose: Button
    private lateinit var btnUpload: Button
    private lateinit var btnStart: Button
    private lateinit var btnClose: Button
    private var currentKmzPath: String? = null
    private var uploadedOk = false
    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri: Uri? = result.data?.data
            if (uri == null) return@registerForActivityResult
            try {
                tvStatus.text = "Kopiuję plik..."
                val localPath = copyUriToExternalFiles(uri)
                if (localPath == null) {
                    tvStatus.text = "Błąd kopiowania pliku"
                    return@registerForActivityResult
                }
                val finalKmz = if (localPath.lowercase().endsWith(".kml")) {
                    tvStatus.text = "Konwersja KML -> KMZ..."
                    KmzMissionHelper.prepareMissionFromUserFile(
                        context = this,
                        filePath = localPath,
                        heightMode = HeightMode.RELATIVE
                    )
                } else {
                    localPath
                }
                currentKmzPath = finalKmz
                uploadedOk = false
                tvPath.text = "Wybrany plik: $finalKmz"
                tvStatus.text = "Plik gotowy. Rysuję trasę..."
                displayKmzOnMap(finalKmz)
            } catch (e: Exception) {
                tvStatus.text = "Błąd: ${e.message}"
                e.printStackTrace()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kmz_preview)
        WPMZManager.getInstance().init(applicationContext)
        tvPath = findViewById(R.id.tvPreviewPath)
        tvStatus = findViewById(R.id.tvPreviewStatus)
        btnChoose = findViewById(R.id.btnChooseKmz)
        btnUpload = findViewById(R.id.btnUploadToDrone)
        btnStart = findViewById(R.id.btnStartOnDrone)
        btnClose = findViewById(R.id.btnClosePreview)
        mapView = findViewById(R.id.mapViewKmzPreview)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
        btnChoose.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            pickFileLauncher.launch(intent)
        }
        btnUpload.setOnClickListener {
            val path = currentKmzPath
            if (path.isNullOrEmpty() || !File(path).exists()) {
                Toast.makeText(this, "Najpierw wybierz KMZ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvStatus.text = "Upload do drona..."
            KmzMissionHelper.uploadKmz(
                path,
                object : CommonCallbacks.CompletionCallbackWithProgress<Double> {
                    override fun onProgressUpdate(progress: Double) {
                        runOnUiThread {
                            tvStatus.text = "Upload: ${(progress * 100).toInt()}%"
                        }
                    }
                    override fun onSuccess() {
                        runOnUiThread {
                            uploadedOk = true
                            tvStatus.text = "Upload OK. Możesz kliknąć START."
                        }
                    }
                    override fun onFailure(error: IDJIError) {
                        runOnUiThread {
                            uploadedOk = false
                            tvStatus.text = "Upload ERROR: ${error.errorCode()} ${error.description()}"
                        }
                    }
                }
            )
        }

        btnStart.setOnClickListener {
            val path = currentKmzPath
            if (path.isNullOrEmpty() || !File(path).exists()) {
                Toast.makeText(this, "Najpierw wybierz KMZ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!uploadedOk) {
                Toast.makeText(this, "Najpierw zrób UPLOAD do drona", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvStatus.text = "Start misji..."
            val missionId = FileUtils.getFileName(path, ".kmz")
            val waylineIDs = WaypointMissionManager.getInstance().getAvailableWaylineIDs(path)
            WaypointMissionManager.getInstance().startMission(
                missionId,
                waylineIDs,
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        runOnUiThread {
                            tvStatus.text = "START OK (komenda przyjęta)"
                        }
                    }
                    override fun onFailure(error: IDJIError) {
                        runOnUiThread {
                            tvStatus.text = "START ERROR: ${error.errorCode()} ${error.description()}"
                        }
                    }
                }
            )
        }

        btnClose.setOnClickListener { finish() }
        intent.getStringExtra("KMZ_PATH")?.let { path ->
            if (File(path).exists()) {
                currentKmzPath = path
                tvPath.text = "Wybrany plik: $path"
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        currentKmzPath?.let { path ->
            if (File(path).exists()) {
                displayKmzOnMap(path)
            }
        }
    }

    private fun copyUriToExternalFiles(uri: Uri): String? {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return null
            val outDir = File(getExternalFilesDir(null), "kmz_preview")
            if (!outDir.exists()) outDir.mkdirs()
            val s = uri.toString().lowercase()
            val ext = when {
                s.contains(".kml") -> ".kml"
                s.contains(".kmz") -> ".kmz"
                else -> ".kmz"
            }
            val outFile = File(outDir, "selected_${System.currentTimeMillis()}$ext")
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
            outFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun displayKmzOnMap(path: String) {
        val map = googleMap ?: return
        try {
            val kmzInfo = WPMZManager.getInstance().getKMZInfo(path)
            val waylines = kmzInfo.waylineWaylinesParseInfo.waylines
            if (waylines.isEmpty()) {
                tvStatus.text = "KMZ: brak wayline’ów"
                return
            }
            map.clear()
            val boundsBuilder = LatLngBounds.Builder()
            var totalWp = 0
            waylines.forEach { wayline ->
                val pts = mutableListOf<LatLng>()
                wayline.waypoints.forEach { wp ->
                    val lat = wp.location.latitude
                    val lon = wp.location.longitude
                    val h = wp.executeHeight
                    val p = LatLng(lat, lon)
                    pts.add(p)
                    boundsBuilder.include(p)
                    map.addMarker(
                        MarkerOptions()
                            .position(p)
                            .title("WP ${wp.waypointIndex}")
                            .snippet("h=${"%.1f".format(h)} m")
                    )
                    totalWp++
                }
                if (pts.size >= 2) {
                    map.addPolyline(PolylineOptions().addAll(pts))
                }
            }
            val bounds = boundsBuilder.build()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            tvStatus.text = "KMZ OK: waylines=${waylines.size}, waypointów=$totalWp"
        } catch (e: Exception) {
            tvStatus.text = "Błąd odczytu KMZ: ${e.message}"
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}
