package pl.twoja.apka.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.twoja.apka.R
import pl.twoja.apka.api.ApiClient
import pl.twoja.apka.api.StartFlightRequest
import pl.twoja.apka.dji.KmzMissionHelper
import kotlin.math.roundToInt

class StartFlightActivity : ComponentActivity() {

    private companion object {
        private const val TAG = "StartFlightActivity"
        private const val WAYPOINT_VISIBLE_ZOOM = 16.0f
        private const val TEST_ROUTE_ID = 5
        private const val TEST_ASSET_KML_PATH = "wpmz/trasa_testowa.kml"
    }

    private lateinit var actOperator: MaterialAutoCompleteTextView
    private lateinit var actRoute: MaterialAutoCompleteTextView
    private lateinit var actDroneModel: MaterialAutoCompleteTextView
    private lateinit var actDroneInstance: MaterialAutoCompleteTextView
    private lateinit var actFlightType: MaterialAutoCompleteTextView
    private lateinit var tilDroneInstance: TextInputLayout
    private lateinit var btnStart: Button
    private lateinit var mapView: MapView
    private var gmap: GoogleMap? = null
    private var plannedPolyline: Polyline? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private val wpMarkers: MutableList<Marker> = mutableListOf()
    private var pendingRouteIdToDraw: Int? = null
    private data class PickItem(val id: Int, val label: String, val raw: Map<String, Any>)
    private data class RoutePoint(val order: Int, val lat: Double, val lon: Double)
    private var operators: List<PickItem> = emptyList()
    private var routes: List<PickItem> = emptyList()
    private var droneModels: List<PickItem> = emptyList()
    private var droneInstances: List<PickItem> = emptyList()
    private var flightTypes: List<PickItem> = emptyList()
    private var selectedOperator: PickItem? = null
    private var selectedRoute: PickItem? = null
    private var selectedDroneModel: PickItem? = null
    private var selectedDroneInstance: PickItem? = null
    private var selectedFlightType: PickItem? = null
    private var instancesRequestToken: Int = 0
    private var startInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start_flight)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        bindViews()
        setupEmptyAdapters()
        setupListeners()
        setupMap(savedInstanceState)
        loadInitialData()
    }

    private fun bindViews() {
        actOperator = findViewById(R.id.actOperator)
        actRoute = findViewById(R.id.actRoute)
        actDroneModel = findViewById(R.id.actDroneModel)
        actDroneInstance = findViewById(R.id.actDroneInstance)
        actFlightType = findViewById(R.id.actFlightType)
        tilDroneInstance = findViewById(R.id.tilDroneInstance)
        btnStart = findViewById(R.id.btnStart)
        mapView = findViewById(R.id.mapView)
        actOperator.threshold = 0
        actRoute.threshold = 0
        actDroneModel.threshold = 0
        actDroneInstance.threshold = 0
        actFlightType.threshold = 0
    }

    private fun setupMap(savedInstanceState: Bundle?) {
        mapView.onCreate(savedInstanceState)
        MapsInitializer.initialize(this)
        mapView.setOnTouchListener { _, _ ->
            mapView.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
        mapView.getMapAsync { map ->
            gmap = map
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = false
            map.uiSettings.isZoomControlsEnabled = false
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(52.0, 19.0), 5.5f))
            map.setOnCameraIdleListener {
                val visible = map.cameraPosition.zoom >= WAYPOINT_VISIBLE_ZOOM
                wpMarkers.forEach { it.isVisible = visible }
            }
            pendingRouteIdToDraw?.let { rid ->
                pendingRouteIdToDraw = null
                drawRouteOnMap(rid)
            }
        }
    }

    private fun setupEmptyAdapters() {
        setDropdown(actOperator, emptyList())
        setDropdown(actRoute, emptyList())
        setDropdown(actDroneModel, emptyList())
        setDropdown(actDroneInstance, emptyList())
        setDropdown(actFlightType, emptyList())
        tilDroneInstance.isEnabled = false
    }

    private fun setupListeners() {
        actOperator.setOnItemClickListener { parent, _, pos, _ ->
            val label = parent.getItemAtPosition(pos)?.toString()
            selectedOperator = operators.firstOrNull { it.label == label } ?: operators.getOrNull(pos)
        }
        actRoute.setOnItemClickListener { parent, _, pos, _ ->
            val label = parent.getItemAtPosition(pos)?.toString()
            selectedRoute = routes.firstOrNull { it.label == label } ?: routes.getOrNull(pos)
            selectedRoute?.id?.let { requestDrawRoute(it) }
        }
        actFlightType.setOnItemClickListener { parent, _, pos, _ ->
            val label = parent.getItemAtPosition(pos)?.toString()
            selectedFlightType = flightTypes.firstOrNull { it.label == label } ?: flightTypes.getOrNull(pos)
        }
        actDroneModel.setOnItemClickListener { parent, _, pos, _ ->
            val label = parent.getItemAtPosition(pos)?.toString()
            selectedDroneModel = droneModels.firstOrNull { it.label == label } ?: droneModels.getOrNull(pos)
            selectedDroneInstance = null
            actDroneInstance.setText("", false)
            setDropdown(actDroneInstance, emptyList())
            tilDroneInstance.isEnabled = false
            val modelId = selectedDroneModel?.id ?: return@setOnItemClickListener
            loadInstancesForModel(modelId)
        }
        actDroneInstance.setOnItemClickListener { parent, _, pos, _ ->
            val label = parent.getItemAtPosition(pos)?.toString()
            selectedDroneInstance = droneInstances.firstOrNull { it.label == label } ?: droneInstances.getOrNull(pos)
        }

        btnStart.setOnClickListener {
            if (startInProgress) return@setOnClickListener
            val op = selectedOperator
            val r = selectedRoute
            val model = selectedDroneModel
            val inst = selectedDroneInstance
            val type = selectedFlightType
            if (op == null || r == null || model == null || inst == null || type == null) {
                Toast.makeText(this, "Uzupełnij wszystkie pola.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (r.id != TEST_ROUTE_ID) {
                Toast.makeText(this, "Na razie tylko trasa testowa id=$TEST_ROUTE_ID.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val plannedTimeS = plannedSecondsFromRoute(r.raw)
            lifecycleScope.launch {
                startInProgress = true
                btnStart.isEnabled = false
                try {
                    val created = ApiClient.api.startFlight(
                        StartFlightRequest(
                            id_operatora = op.id,
                            id_drona = inst.id,
                            id_trasy = r.id,
                            id_typ = type.id
                        )
                    )
                    val flightId = created.id_lotu
                    getSharedPreferences("app", MODE_PRIVATE)
                        .edit()
                        .putInt("ACTIVE_FLIGHT_ID", flightId)
                        .apply()
                    startMissionFromAssetsAndGoLive(
                        routeId = r.id,
                        plannedTimeS = plannedTimeS,
                        flightId = flightId
                    )
                } catch (e: Exception) {
                    startInProgress = false
                    btnStart.isEnabled = true
                    Log.e(TAG, "startFlight error", e)
                    Toast.makeText(this@StartFlightActivity, "Błąd zapisu lotu: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun markFlightAborted(flightId: Int, reason: String) {
        Log.w(TAG, "markFlightAborted($flightId): $reason")
        lifecycleScope.launch {
            try {
                ApiClient.api.abortFlight(flightId)
            } catch (e: Exception) {
                Log.e(TAG, "abortFlight error", e)
            } finally {
                getSharedPreferences("app", MODE_PRIVATE).edit().remove("ACTIVE_FLIGHT_ID").apply()
            }
        }
    }

    private fun startMissionFromAssetsAndGoLive(routeId: Int, plannedTimeS: Int, flightId: Int) {
        Toast.makeText(this, "Przygotowanie misji…", Toast.LENGTH_SHORT).show()
        try {
            val kmzPath = KmzMissionHelper.generateKmzFromKmlAsset(
                context = this,
                assetPath = TEST_ASSET_KML_PATH,
                defaultHeightMeters = 30.0,
                defaultSpeed = 3.0
            )
            KmzMissionHelper.uploadKmz(
                kmzPath,
                object : CommonCallbacks.CompletionCallbackWithProgress<Double> {
                    override fun onProgressUpdate(progress: Double) {
                        runOnUiThread {
                            val pct = (progress * 100.0).roundToInt()
                            Toast.makeText(this@StartFlightActivity, "Upload misji: $pct%", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onSuccess() {
                        runOnUiThread {
                            Toast.makeText(this@StartFlightActivity, "Upload OK. Start misji…", Toast.LENGTH_SHORT).show()
                        }
                        KmzMissionHelper.startMissionFromPath(
                            kmzPath,
                            object : CommonCallbacks.CompletionCallback {
                                override fun onSuccess() {
                                    runOnUiThread {
                                        startInProgress = false
                                        btnStart.isEnabled = true

                                        val i = Intent(this@StartFlightActivity, FlightLiveActivity::class.java)
                                        i.putExtra("route_id", routeId)
                                        i.putExtra("planned_time_s", plannedTimeS)
                                        i.putExtra("flight_id", flightId)
                                        i.putExtra("use_phone_location", false)
                                        startActivity(i)
                                    }
                                }
                                override fun onFailure(error: IDJIError) {
                                    runOnUiThread {
                                        startInProgress = false
                                        btnStart.isEnabled = true
                                        Toast.makeText(
                                            this@StartFlightActivity,
                                            "startMission ERROR: ${error.errorCode()} ${error.description()}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    markFlightAborted(flightId, "startMission failure: ${error.description()}")
                                }
                            }
                        )
                    }

                    override fun onFailure(error: IDJIError) {
                        runOnUiThread {
                            startInProgress = false
                            btnStart.isEnabled = true
                            Toast.makeText(
                                this@StartFlightActivity,
                                "Upload ERROR: ${error.errorCode()} ${error.description()}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        markFlightAborted(flightId, "upload failure: ${error.description()}")
                    }
                }
            )

        } catch (e: Exception) {
            startInProgress = false
            btnStart.isEnabled = true
            Log.e(TAG, "startMissionFromAssetsAndGoLive error", e)
            Toast.makeText(this, "Błąd: ${e.message}", Toast.LENGTH_LONG).show()
            markFlightAborted(flightId, "exception: ${e.message}")
        }
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            try {
                val opDef = async { ApiClient.api.listOperator() }
                val routesDef = async { ApiClient.api.getRoutes() }
                val modelsDef = async { ApiClient.api.listDroneModels() }
                val typesDef = async { ApiClient.api.listFlightTypes() }
                val opRaw = opDef.await()
                val routesRaw = routesDef.await()
                val modelsRaw = modelsDef.await()
                val typesRaw = typesDef.await()
                operators = opRaw.mapNotNull { m ->
                    val mm = m as Map<String, Any>
                    val id = intFrom(mm, "id_operatora") ?: return@mapNotNull null
                    val imie = (strFrom(mm, "imie") ?: "").trim()
                    val nazw = (strFrom(mm, "nazwisko") ?: "").trim()
                    PickItem(id, "$imie $nazw".trim(), mm)
                }
                routes = routesRaw.mapNotNull { m ->
                    val mm = m as Map<String, Any>
                    val id = intFrom(mm, "id_trasy") ?: return@mapNotNull null
                    val name = (strFrom(mm, "nazwa") ?: "").trim()
                    PickItem(id, name, mm)
                }
                droneModels = modelsRaw.mapNotNull { m ->
                    val id = intFrom(m as Map<String, Any>, "id_modelu") ?: return@mapNotNull null
                    val prod = (strFrom(m, "producent") ?: "").trim()
                    val name = (strFrom(m, "nazwa_modelu") ?: "").trim()
                    PickItem(id, listOf(prod, name).filter { it.isNotBlank() }.joinToString(" "), m)
                }
                flightTypes = typesRaw.mapNotNull { m ->
                    val id = intFrom(m as Map<String, Any>, "id_typ") ?: return@mapNotNull null
                    val name = (strFrom(m, "nazwa") ?: "").trim()
                    PickItem(id, name, m)
                }
                setDropdown(actOperator, operators.map { it.label })
                setDropdown(actRoute, routes.map { it.label })
                setDropdown(actDroneModel, droneModels.map { it.label })
                setDropdown(actFlightType, flightTypes.map { it.label })
            } catch (e: Exception) {
                Log.e(TAG, "loadInitialData error", e)
                Toast.makeText(this@StartFlightActivity, "Błąd pobierania danych: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadInstancesForModel(modelId: Int) {
        val token = ++instancesRequestToken
        lifecycleScope.launch {
            try {
                tilDroneInstance.isEnabled = false
                setDropdown(actDroneInstance, emptyList())
                actDroneInstance.setText("", false)
                val raw = ApiClient.api.getDroneInstances(modelId)
                if (token != instancesRequestToken) return@launch
                droneInstances = raw.mapNotNull { m ->
                    val mm = m as Map<String, Any>
                    val id = intFrom(mm, "id_drona") ?: return@mapNotNull null
                    val sn = (strFrom(mm, "numer_seryjny") ?: "").trim()
                    PickItem(id, if (sn.isNotBlank()) sn else "brak SN", mm)
                }
                setDropdown(actDroneInstance, droneInstances.map { it.label })
                tilDroneInstance.isEnabled = droneInstances.isNotEmpty()
            } catch (e: Exception) {
                Log.e(TAG, "loadInstancesForModel($modelId) error", e)
                Toast.makeText(this@StartFlightActivity, "Błąd pobierania egzemplarzy: ${e.message}", Toast.LENGTH_LONG).show()
                tilDroneInstance.isEnabled = false
            }
        }
    }

    private fun requestDrawRoute(routeId: Int) {
        val map = gmap
        if (map == null) pendingRouteIdToDraw = routeId else drawRouteOnMap(routeId)
    }

    private fun clearRouteOverlays() {
        plannedPolyline?.remove()
        plannedPolyline = null
        startMarker?.remove()
        startMarker = null
        endMarker?.remove()
        endMarker = null
        wpMarkers.forEach { it.remove() }
        wpMarkers.clear()
    }

    private fun drawRouteOnMap(routeId: Int) {
        val map = gmap ?: run {
            pendingRouteIdToDraw = routeId
            return
        }
        lifecycleScope.launch {
            try {
                clearRouteOverlays()
                val rawPoints: List<Map<String, Any?>> = withContext(Dispatchers.IO) {
                    ApiClient.api.getRoutePoints(routeId)
                }
                val pts = parseRoutePoints(rawPoints)
                if (pts.size < 2) {
                    Toast.makeText(this@StartFlightActivity, "Trasa nie ma punktów (min. 2).", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val latLngs = pts.map { LatLng(it.lat, it.lon) }
                plannedPolyline = map.addPolyline(
                    PolylineOptions()
                        .addAll(latLngs)
                        .width(8f)
                        .color(ContextCompat.getColor(this@StartFlightActivity, R.color.colorPrimary))
                )
                val startIcon = makeCircleMarkerIcon(
                    sizeDp = 44f,
                    fillColor = ContextCompat.getColor(this@StartFlightActivity, R.color.colorPrimary),
                    strokeColor = 0,
                    strokeDp = 0f,
                    text = "S",
                    textColor = ContextCompat.getColor(this@StartFlightActivity, R.color.colorOnPrimary),
                    textSp = 16f,
                    bold = true
                )
                val endIcon = makeCircleMarkerIcon(
                    sizeDp = 44f,
                    fillColor = ContextCompat.getColor(this@StartFlightActivity, R.color.colorSurfaceVariant),
                    strokeColor = 0,
                    strokeDp = 0f,
                    text = "K",
                    textColor = ContextCompat.getColor(this@StartFlightActivity, R.color.colorOnSurface),
                    textSp = 16f,
                    bold = true
                )
                startMarker = map.addMarker(
                    MarkerOptions()
                        .position(latLngs.first())
                        .title("Start")
                        .icon(startIcon)
                        .anchor(0.5f, 0.5f)
                        .zIndex(2f)
                )
                endMarker = map.addMarker(
                    MarkerOptions()
                        .position(latLngs.last())
                        .title("Koniec")
                        .icon(endIcon)
                        .anchor(0.5f, 0.5f)
                        .zIndex(2f)
                )
                val wpIconSize = 22f
                for (i in latLngs.indices) {
                    if (i == 0 || i == latLngs.lastIndex) continue
                    val icon = makeCircleMarkerIcon(
                        sizeDp = wpIconSize,
                        fillColor = ContextCompat.getColor(this@StartFlightActivity, R.color.colorSurfaceVariant),
                        strokeColor = ContextCompat.getColor(this@StartFlightActivity, R.color.colorPrimary),
                        strokeDp = 1.5f,
                        text = (i + 1).toString(),
                        textColor = ContextCompat.getColor(this@StartFlightActivity, R.color.colorOnSurface),
                        textSp = 10f,
                        bold = true
                    )
                    val m = map.addMarker(
                        MarkerOptions()
                            .position(latLngs[i])
                            .title("Punkt ${i + 1}")
                            .icon(icon)
                            .anchor(0.5f, 0.5f)
                            .zIndex(1f)
                    )
                    if (m != null) wpMarkers.add(m)
                }
                zoomMapToRoute(latLngs)
                val visible = map.cameraPosition.zoom >= WAYPOINT_VISIBLE_ZOOM
                wpMarkers.forEach { it.isVisible = visible }
            } catch (e: Exception) {
                Log.e(TAG, "drawRouteOnMap($routeId) error", e)
                Toast.makeText(this@StartFlightActivity, "Błąd rysowania trasy: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun zoomMapToRoute(latLngs: List<LatLng>) {
        val map = gmap ?: return
        if (latLngs.isEmpty()) return
        mapView.post {
            try {
                if (latLngs.size == 1) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLngs.first(), 17f))
                    return@post
                }
                val b = LatLngBounds.builder()
                latLngs.forEach { b.include(it) }
                val bounds = b.build()
                val paddingPx = (resources.displayMetrics.density * 64).roundToInt()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
            } catch (e: Exception) {
                Log.w(TAG, "zoomMapToRoute failed: ${e.message}")
            }
        }
    }

    private fun parseRoutePoints(list: List<Map<String, Any?>>): List<RoutePoint> {
        return list.mapNotNull { m ->
            val order = (m["kolejnosc"] as? Number)?.toInt()
                ?: m["kolejnosc"]?.toString()?.toIntOrNull()
                ?: m["lp"]?.toString()?.toIntOrNull()
            val lat = (m["lat"] as? Number)?.toDouble()
                ?: m["latitude"]?.toString()?.toDoubleOrNull()
            val lon = (m["lon"] as? Number)?.toDouble()
                ?: (m["lng"] as? Number)?.toDouble()
                ?: m["longitude"]?.toString()?.toDoubleOrNull()
            if (order != null && lat != null && lon != null) RoutePoint(order, lat, lon) else null
        }.sortedBy { it.order }
    }

    private fun plannedSecondsFromRoute(routeMap: Map<String, Any>): Int {
        val min = (routeMap["planowany_czas_min"] as? Number)?.toDouble()
            ?: routeMap["planowany_czas_min"]?.toString()?.toDoubleOrNull()
            ?: (routeMap["czas_min"] as? Number)?.toDouble()
            ?: routeMap["czas_min"]?.toString()?.toDoubleOrNull()
        if (min != null && min > 0) return (min * 60.0).roundToInt()
        val sec = (routeMap["planowany_czas_s"] as? Number)?.toInt()
            ?: routeMap["planowany_czas_s"]?.toString()?.toIntOrNull()
        return sec ?: 0
    }

    private fun makeCircleMarkerIcon(
        sizeDp: Float,
        fillColor: Int,
        strokeColor: Int,
        strokeDp: Float,
        text: String,
        textColor: Int,
        textSp: Float,
        bold: Boolean
    ): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).roundToInt().coerceAtLeast(16)
        val strokePx = (strokeDp * density).coerceAtLeast(0f)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val r = (sizePx / 2f) - strokePx
        val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillColor
        }
        canvas.drawCircle(cx, cy, r, paintFill)
        if (strokePx > 0.5f) {
            val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = strokeColor
                strokeWidth = strokePx
            }
            canvas.drawCircle(cx, cy, r, paintStroke)
        }
        val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textAlign = Paint.Align.CENTER
            textSize = textSp * density
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val bounds = Rect()
        paintText.getTextBounds(text, 0, text.length, bounds)
        val textY = cy + bounds.height() / 2f
        canvas.drawText(text, cx, textY, paintText)
        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    private fun setDropdown(act: MaterialAutoCompleteTextView, labels: List<String>) {
        act.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels))
    }

    private fun strFrom(m: Map<String, Any>, key: String): String? =
        m[key]?.let { if (it is String) it else it.toString() }

    private fun intFrom(m: Map<String, Any>, vararg keys: String): Int? {
        for (k in keys) {
            val v = m[k] ?: continue
            val parsed = when (v) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull()
                else -> null
            }
            if (parsed != null) return parsed
        }
        return null
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
