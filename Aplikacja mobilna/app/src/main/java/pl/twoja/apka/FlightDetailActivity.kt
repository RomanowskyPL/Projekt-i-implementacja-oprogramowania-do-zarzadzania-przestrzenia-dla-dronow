package pl.twoja.apka

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.twoja.apka.api.ApiClient
import pl.twoja.apka.ui.FlightMoreInfoActivity
import java.util.Locale
import kotlin.math.roundToInt

class FlightDetailActivity : ComponentActivity() {

    private var flightId: Int = -1
    private lateinit var mapView: MapView
    private var gmap: GoogleMap? = null
    private var plannedPoints: List<LatLng> = emptyList()
    private var plannedPolyline: Polyline? = null
    private var plannedStartMarker: Marker? = null
    private var plannedEndMarker: Marker? = null
    private val wpMarkers: MutableList<Marker> = mutableListOf()
    private val waypointVisibleZoom = 16.0f
    private data class TelemetryPoint(val lat: Double, val lon: Double, val alt: Double?)
    private var telemetryPoints: List<TelemetryPoint> = emptyList()
    private var telemetryPolyline: Polyline? = null
    private val telemetryMarkers: MutableList<Marker> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flight_detail)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        flightId = intent.getIntExtra("flight_id", -1)
        if (flightId == -1) {
            Toast.makeText(this, "Brak ID lotu", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<View?>(R.id.cardMoreInfo)?.setOnClickListener {
            val i = Intent(this, FlightMoreInfoActivity::class.java)
            i.putExtra("flight_id", flightId)
            startActivity(i)
        }

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        MapsInitializer.initialize(this)
        setupMapTouchFix()

        mapView.getMapAsync { map ->
            gmap = map
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            map.uiSettings.apply {
                isZoomControlsEnabled = false
                isZoomGesturesEnabled = true
                isScrollGesturesEnabled = true
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
            }
            map.setOnMarkerClickListener { m ->
                val tp = m.tag as? TelemetryPoint
                if (tp != null) {
                    val msg = buildString {
                        append("Współrzędne: ")
                        append(String.format(Locale("pl","PL"), "%.6f, %.6f", tp.lat, tp.lon))
                        if (tp.alt != null) {
                            append("\nWysokość: ")
                            append(String.format(Locale("pl","PL"), "%.2f m", tp.alt))
                        }
                    }
                    Toast.makeText(this@FlightDetailActivity, msg, Toast.LENGTH_LONG).show()
                }
                false
            }
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(52.0, 19.0), 5.5f))
            map.setOnCameraIdleListener {
                updateWaypointVisibility(map)
            }
            renderPlannedIfReady()
            renderTelemetryIfReady()
        }
        loadFlightRouteAndTelemetry()
    }

    private fun setupMapTouchFix() {
        val scroll = findViewById<NestedScrollView>(R.id.scroll)

        mapView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    scroll.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    scroll.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    scroll.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount <= 1) {
                        scroll.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }
            false
        }
    }

    private fun loadFlightRouteAndTelemetry() {
        lifecycleScope.launch {
            try {
                val r: Map<String, Any?> = withContext(Dispatchers.IO) {
                    ApiClient.api.getFlight(flightId)
                }
                val idTrasy = r.intOf("id_trasy")
                findViewById<TextView>(R.id.tvTitle).text = r.strOf("nazwa_trasy") ?: "—"
                findViewById<TextView>(R.id.tvStart).text = "Start: ${formatApiDateTime(r.strOf("czas_startu"))}"
                findViewById<TextView>(R.id.tvLanding).text = "Lądowanie: ${formatApiDateTime(r.strOf("czas_konca"))}"
                val dist = r.doubleOf("rzeczywista_dlugosc_lotu_m")?.let { "%.1f m".format(it) } ?: "—"
                findViewById<TextView>(R.id.tvDistance).text = "Długość lotu: $dist"
                val dur = r.intOf("czas_trwania_s")?.let { formatSeconds(it) } ?: "—"
                findViewById<TextView>(R.id.tvDuration).text = "Czas lotu: $dur"
                findViewById<TextView>(R.id.tvType).text = "Typ lotu: ${r.strOf("typ_lotu") ?: "—"}"
                findViewById<TextView>(R.id.tvStatus).text = "Status: ${r.strOf("status") ?: "—"}"
                val imie = r.strOf("imie")?.trim().orEmpty()
                val nazw = r.strOf("nazwisko")?.trim().orEmpty()
                val operatorFull = (imie + " " + nazw).trim().ifBlank { "—" }
                findViewById<TextView>(R.id.tvOperator).text = "Operator: $operatorFull"
                findViewById<TextView>(R.id.tvOperatorUid).text = "UID operatora: ${r.strOf("uid_operatora") ?: "—"}"
                val producent = r.strOf("producent")?.trim()
                val model = r.strOf("nazwa_modelu")?.trim()
                val droneName = listOfNotNull(producent, model).joinToString(" ").ifBlank { "—" }
                findViewById<TextView>(R.id.tvDroneName).text = "Nazwa drona: $droneName"
                findViewById<TextView>(R.id.tvDroneSn).text = "Numer seryjny drona: ${r.strOf("numer_seryjny") ?: "—"}"
                plannedPoints = loadPlannedPoints(flightId, idTrasy)
                renderPlannedIfReady()
                telemetryPoints = loadTelemetryPoints(flightId)
                renderTelemetryIfReady()
                fitCameraToAll()
            } catch (e: Exception) {
                Log.e("FlightDetail", "load error", e)
                Toast.makeText(this@FlightDetailActivity, "Błąd: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun loadPlannedPoints(flightId: Int, idTrasy: Int?): List<LatLng> {
        try {
            val list: List<Map<String, Any?>> = withContext(Dispatchers.IO) {
                ApiClient.api.getFlightRoutePoints(flightId)
            }
            val pts = parsePlannedPoints(list)
            if (pts.size >= 2) return pts
        } catch (_: Exception) {}
        if (idTrasy != null && idTrasy > 0) {
            try {
                val list: List<Map<String, Any?>> = withContext(Dispatchers.IO) {
                    ApiClient.api.getRoutePoints(idTrasy)
                }
                val pts = parsePlannedPoints(list)
                if (pts.size >= 2) return pts
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    private fun parsePlannedPoints(list: List<Map<String, Any?>>): List<LatLng> {
        return list
            .mapNotNull { p ->
                val order = p.intOf("kolejnosc")
                val lat = p.doubleOf("lat")
                val lon = p.doubleOf("lon") ?: p.doubleOf("lng")
                if (order != null && lat != null && lon != null) Triple(order, lat, lon) else null
            }
            .sortedBy { it.first }
            .map { LatLng(it.second, it.third) }
    }

    private suspend fun loadTelemetryPoints(flightId: Int): List<TelemetryPoint> {
        val list: List<Map<String, Any?>> = withContext(Dispatchers.IO) {
            ApiClient.api.getFlightTelemetry(flightId)
        }
        return list.mapNotNull { m ->
            val lat = m.doubleOf("lat")
            val lon = m.doubleOf("lon")
            val alt = m.doubleOf("wysokosc_m") ?: m.doubleOf("alt")
            if (lat != null && lon != null) TelemetryPoint(lat, lon, alt) else null
        }
    }

    private fun renderPlannedIfReady() {
        val map = gmap ?: return
        if (plannedPoints.size < 2) return
        plannedPolyline?.remove()
        plannedStartMarker?.remove()
        plannedEndMarker?.remove()
        wpMarkers.forEach { it.remove() }
        wpMarkers.clear()
        plannedPolyline = map.addPolyline(
            PolylineOptions()
                .addAll(plannedPoints)
                .width(8f)
                .color(ContextCompat.getColor(this, R.color.colorPrimary))
        )
        val startIcon = makeCircleMarkerIcon(
            sizeDp = 44f,
            fillColor = ContextCompat.getColor(this, R.color.colorPrimary),
            strokeColor = 0,
            strokeDp = 0f,
            text = "S",
            textColor = ContextCompat.getColor(this, R.color.colorOnPrimary),
            textSp = 16f,
            bold = true
        )
        val endIcon = makeCircleMarkerIcon(
            sizeDp = 44f,
            fillColor = ContextCompat.getColor(this, R.color.colorSurfaceVariant),
            strokeColor = 0,
            strokeDp = 0f,
            text = "K",
            textColor = ContextCompat.getColor(this, R.color.colorOnSurface),
            textSp = 16f,
            bold = true
        )
        plannedStartMarker = map.addMarker(
            MarkerOptions()
                .position(plannedPoints.first())
                .title("Start")
                .icon(startIcon)
                .anchor(0.5f, 0.5f)
        )
        plannedEndMarker = map.addMarker(
            MarkerOptions()
                .position(plannedPoints.last())
                .title("Koniec")
                .icon(endIcon)
                .anchor(0.5f, 0.5f)
        )
        val wpIconSize = 22f
        for (i in plannedPoints.indices) {
            if (i == 0 || i == plannedPoints.lastIndex) continue

            val icon = makeCircleMarkerIcon(
                sizeDp = wpIconSize,
                fillColor = ContextCompat.getColor(this, R.color.colorSurfaceVariant),
                strokeColor = ContextCompat.getColor(this, R.color.colorPrimary),
                strokeDp = 1.5f,
                text = (i + 1).toString(),
                textColor = ContextCompat.getColor(this, R.color.colorOnSurface),
                textSp = 10f,
                bold = true
            )
            val m = map.addMarker(
                MarkerOptions()
                    .position(plannedPoints[i])
                    .title("Punkt ${i + 1}")
                    .icon(icon)
                    .anchor(0.5f, 0.5f)
            )
            if (m != null) wpMarkers.add(m)
        }
        updateWaypointVisibility(map)
    }

    private fun renderTelemetryIfReady() {
        val map = gmap ?: return
        if (telemetryPoints.size < 2) return
        telemetryPolyline?.remove()
        telemetryMarkers.forEach { it.remove() }
        telemetryMarkers.clear()
        val latLngs = telemetryPoints.map { LatLng(it.lat, it.lon) }
        telemetryPolyline = map.addPolyline(
            PolylineOptions()
                .addAll(latLngs)
                .width(7f)
                .color(ContextCompat.getColor(this, R.color.colorOnSurfaceVariant))
        )
        val step = when {
            telemetryPoints.size <= 80 -> 1
            telemetryPoints.size <= 250 -> 2
            telemetryPoints.size <= 800 -> 5
            else -> 10
        }
        for (i in telemetryPoints.indices) {
            val important = (i == 0) || (i == telemetryPoints.lastIndex) || (i % step == 0)
            if (!important) continue
            val tp = telemetryPoints[i]
            val label = (i + 1).toString()
            val sizeDp = when {
                label.length <= 2 -> 18f
                label.length == 3 -> 22f
                else -> 26f
            }
            val icon = makeCircleMarkerIcon(
                sizeDp = sizeDp,
                fillColor = ContextCompat.getColor(this, R.color.colorSurfaceVariant),
                strokeColor = 0,
                strokeDp = 0f,
                text = label,
                textColor = ContextCompat.getColor(this, R.color.colorOnSurface),
                textSp = when {
                    label.length <= 2 -> 9f
                    label.length == 3 -> 9.5f
                    else -> 10f
                },
                bold = true
            )
            val m = map.addMarker(
                MarkerOptions()
                    .position(LatLng(tp.lat, tp.lon))
                    .title("Telemetry #$label")
                    .icon(icon)
                    .anchor(0.5f, 0.5f)
            )
            m?.tag = tp
            if (m != null) telemetryMarkers.add(m)
        }
    }

    private fun fitCameraToAll() {
        val map = gmap ?: return
        val all = mutableListOf<LatLng>()
        all.addAll(plannedPoints)
        all.addAll(telemetryPoints.map { LatLng(it.lat, it.lon) })
        if (all.size < 2) return
        try {
            val b = LatLngBounds.builder()
            all.forEach { b.include(it) }
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    b.build(),
                    (resources.displayMetrics.density * 56).roundToInt()
                )
            )
        } catch (_: Exception) {}
    }

    private fun updateWaypointVisibility(map: GoogleMap) {
        val visible = map.cameraPosition.zoom >= waypointVisibleZoom
        wpMarkers.forEach { it.isVisible = visible }
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
        if (strokeDp > 0f && strokeColor != 0) {
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

    private fun formatSeconds(total: Int): String {
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun formatApiDateTime(input: String?): String {
        if (input.isNullOrBlank() || input == "null") return "—"
        return input.replace("T", " ").replace(Regex("\\.\\d+.*$"), "").trim()
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

    private fun Map<String, Any?>.strOf(key: String): String? = this[key]?.toString()
    private fun Map<String, Any?>.intOf(key: String): Int? =
        (this[key] as? Number)?.toInt() ?: this[key]?.toString()?.toIntOrNull()
    private fun Map<String, Any?>.doubleOf(key: String): Double? =
        (this[key] as? Number)?.toDouble() ?: this[key]?.toString()?.toDoubleOrNull()
}
