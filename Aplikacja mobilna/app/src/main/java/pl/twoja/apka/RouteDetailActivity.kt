package pl.twoja.apka

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.TextView
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import pl.twoja.apka.api.ApiClient
import java.util.Locale
import kotlin.math.roundToInt

class RouteDetailActivity : ComponentActivity() {

    private lateinit var tvName: TextView
    private lateinit var tvLen: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvOpis: TextView
    private lateinit var mapView: MapView
    private var gmap: GoogleMap? = null
    private var routePoints: List<LatLng> = emptyList()
    private var routePolyline: Polyline? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private val wpMarkers: MutableList<Marker> = mutableListOf()
    private val waypointVisibleZoom = 16.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_detail)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tvName = findViewById(R.id.tvName)
        tvLen  = findViewById(R.id.tvLen)
        tvTime = findViewById(R.id.tvTime)
        tvOpis = findViewById(R.id.tvOpis)
        mapView = findViewById(R.id.mapView)
        findViewById<FloatingActionButton>(R.id.fabBack).setOnClickListener { finish() }
        tvName.text = intent.getStringExtra("name")
            ?: intent.getStringExtra("route_name")
                    ?: ""

        val lenD = intent.getDoubleExtra("len", -1.0)
        if (lenD >= 0.0) tvLen.text = "Planowana długość: ${formatMeters2(lenD)} m"
        val timeS = intent.getIntExtra("time_s", -1)
        if (timeS >= 0) tvTime.text = "Planowany czas: ${formatMmSs(timeS)}"

        (intent.getStringExtra("opis") ?: "")
            .takeIf { it.isNotBlank() }
            ?.let { tvOpis.text = it }
        val id = intent.getIntExtra("id", intent.getIntExtra("route_id", -1))
        mapView.onCreate(savedInstanceState)
        MapsInitializer.initialize(this)
        mapView.getMapAsync { map ->
            gmap = map
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(52.0, 19.0), 5.5f))
            map.setOnCameraIdleListener {
                updateWaypointVisibility(map)
            }
            renderRouteIfReady()
        }

        lifecycleScope.launch {
            runCatching { if (id > 0) ApiClient.api.getRoute(id) else emptyMap() }
                .onSuccess { m ->
                    if (m.isNotEmpty()) {
                        tvName.text = (m["nazwa"] ?: tvName.text).toString()
                        val len = (m["planowana_dlugosc_m"] as? Number)?.toDouble()
                            ?: m["planowana_dlugosc_m"]?.toString()?.toDoubleOrNull()
                        val timeMin = (m["planowany_czas_min"] as? Number)?.toDouble()
                            ?: m["planowany_czas_min"]?.toString()?.toDoubleOrNull()
                        val seconds = timeMin?.let { (it * 60.0).roundToInt() }
                        tvLen.text = "Planowana długość: " + (len?.let { "${formatMeters2(it)} m" } ?: "—")
                        tvTime.text = "Planowany czas: " + (seconds?.let { formatMmSs(it) } ?: "—")
                        tvOpis.text = (m["opis"] ?: tvOpis.text).toString()
                    }
                }

            runCatching { if (id > 0) ApiClient.api.getRoutePoints(id) else emptyList() }
                .onSuccess { list ->
                    routePoints = list
                        .mapNotNull { p ->
                            val order = (p["kolejnosc"] as? Number)?.toInt()
                                ?: p["kolejnosc"]?.toString()?.toIntOrNull()
                            val lat = (p["lat"] as? Number)?.toDouble()
                                ?: p["lat"]?.toString()?.toDoubleOrNull()
                            val lon = (p["lon"] as? Number)?.toDouble()
                                ?: (p["lng"] as? Number)?.toDouble()
                                ?: p["lon"]?.toString()?.toDoubleOrNull()
                                ?: p["lng"]?.toString()?.toDoubleOrNull()
                            if (lat != null && lon != null && order != null) Triple(order, lat, lon) else null
                        }
                        .sortedBy { it.first }
                        .map { LatLng(it.second, it.third) }
                    renderRouteIfReady()
                }
        }
    }

    private fun renderRouteIfReady() {
        val map = gmap ?: return
        if (routePoints.size < 2) return
        routePolyline?.remove()
        startMarker?.remove()
        endMarker?.remove()
        wpMarkers.forEach { it.remove() }
        wpMarkers.clear()
        val lineColor = ContextCompat.getColor(this, R.color.colorPrimary)
        routePolyline = map.addPolyline(
            PolylineOptions()
                .addAll(routePoints)
                .width(8f)
                .color(lineColor)
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
            textColor = ContextCompat.getColor(this, R.color.colorOnBackground),
            textSp = 16f,
            bold = true
        )

        startMarker = map.addMarker(
            MarkerOptions()
                .position(routePoints.first())
                .title("Start")
                .icon(startIcon)
                .anchor(0.5f, 0.5f)
        )

        endMarker = map.addMarker(
            MarkerOptions()
                .position(routePoints.last())
                .title("Koniec")
                .icon(endIcon)
                .anchor(0.5f, 0.5f)
        )

        val wpIconSize = 22f // dp: małe
        for (i in routePoints.indices) {
            val isFirst = i == 0
            val isLast = i == routePoints.lastIndex
            if (isFirst || isLast) continue
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
                    .position(routePoints[i])
                    .title("Punkt ${i + 1}")
                    .icon(icon)
                    .anchor(0.5f, 0.5f)
                    .zIndex(1f)
            )
            if (m != null) wpMarkers.add(m)
        }

        try {
            val b = LatLngBounds.builder()
            routePoints.forEach { b.include(it) }
            val bounds = b.build()
            val paddingPx = (resources.displayMetrics.density * 56).toInt()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
        } catch (_: Exception) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(routePoints.first(), 16f))
        }

        updateWaypointVisibility(map)
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
        val strokePx = (strokeDp * density).coerceAtLeast(1f)
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

    private fun formatMeters2(meters: Double): String =
        String.format(Locale("pl", "PL"), "%.2f", meters)

    private fun formatMmSs(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val mm = s / 60
        val ss = s % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mm, ss)
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
