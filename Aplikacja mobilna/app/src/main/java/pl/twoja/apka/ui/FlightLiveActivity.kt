package pl.twoja.apka.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.button.MaterialButton
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.v5.common.callback.CommonCallbacks
import dji.v5.manager.KeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.twoja.apka.R
import pl.twoja.apka.api.ApiClient
import pl.twoja.apka.api.TelemetryCreateRequest
import pl.twoja.apka.telemetry.TelemetrySession
import java.util.Locale
import kotlin.math.roundToInt

class FlightLiveActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "FlightLiveActivity"
        private const val UI_REFRESH_MS = 3000L
        private const val REQ_LOC = 7001
        private const val MIN_VALID_LAT = -90.0
        private const val MAX_VALID_LAT = 90.0
        private const val MIN_VALID_LON = -180.0
        private const val MAX_VALID_LON = 180.0
    }

    private lateinit var tvRemaining: TextView
    private lateinit var tvPosition: TextView
    private lateinit var btnAbort: MaterialButton
    private var gmap: GoogleMap? = null
    private var plannedRoute: List<LatLng> = emptyList()
    private var plannedPolyline: Polyline? = null
    private val wpMarkers: MutableList<Marker> = mutableListOf()
    private val waypointVisibleZoom = 16.0f
    private var droneMarker: Marker? = null
    private val livePath: MutableList<LatLng> = mutableListOf()
    private var livePolyline: Polyline? = null
    @Volatile private var lastDjiLocation: LocationCoordinate3D? = null
    @Volatile private var lastPhoneLocation: Location? = null
    @Volatile private var lastIsFlyingNow: Boolean = false
    private var usePhoneLocation: Boolean = false
    private var uiRefreshJob: Job? = null
    private var countdownTimer: CountDownTimer? = null
    private val djiLocationHolder = Any()
    private val djiFlyingHolder = Any()
    private var locationManager: LocationManager? = null
    private var phoneListener: LocationListener? = null
    private var flightWasAirborne: Boolean = false
    private var landingHandled: Boolean = false
    private var finishSent: Boolean = false
    private var abortSent: Boolean = false
    @Volatile private var telemetryUploadEnabled: Boolean = true
    @Volatile private var telemetryUploadInProgress: Boolean = false
    private var routeId: Int = -1
    private var plannedTimeS: Int = 0
    private var flightId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flight_live)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tvRemaining = findViewById(R.id.tvRemaining)
        tvPosition = findViewById(R.id.tvPosition)
        btnAbort = findViewById(R.id.btnAbort)
        routeId = intent.getIntExtra("route_id", -1)
        plannedTimeS = intent.getIntExtra("planned_time_s", 0)
        usePhoneLocation = intent.getBooleanExtra("use_phone_location", false)
        flightId = intent.getIntExtra("flight_id", -1)
        if (routeId <= 0) {
            Toast.makeText(this, "Brak route_id", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        btnAbort.setOnClickListener {
            if (!usePhoneLocation) {
                abortFlightAndGoSummary()
            } else {
                goSummary()
            }
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync { map ->
            gmap = map
            setupMapUi(map)
            map.setOnCameraIdleListener {
                val visible = map.cameraPosition.zoom >= waypointVisibleZoom
                wpMarkers.forEach { it.isVisible = visible }
            }
            renderPlannedRouteIfReady()
        }

        startCountdownIfNeeded(plannedTimeS)
        loadPlannedRoute(routeId)
        if (usePhoneLocation) {
            startPhoneLocation()
            if (!TelemetrySession.isActive()) {
                TelemetrySession.startNewSession()
                TelemetrySession.addEvent("TRYB DEMO: start śledzenia (telefon)")
            }
        } else {
            startDjiLocationListener()
            startDjiFlyingListener()

            if (!TelemetrySession.isActive()) {
                TelemetrySession.startNewSession()
                TelemetrySession.addEvent("MISJA: FlightLiveActivity uruchomione")
            }
        }
        startUiRefreshLoop()
    }
    override fun onDestroy() {
        super.onDestroy()
        telemetryUploadEnabled = false
        stopUiRefreshLoop()
        if (usePhoneLocation) stopPhoneLocation() else {
            stopDjiLocationListener()
            stopDjiFlyingListener()
        }
        countdownTimer?.cancel()
        countdownTimer = null
    }

    private fun setupMapUi(map: GoogleMap) {
        map.uiSettings.isCompassEnabled = true
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isScrollGesturesEnabled = true
        map.uiSettings.isZoomGesturesEnabled = true
        map.uiSettings.isRotateGesturesEnabled = true
        map.uiSettings.isTiltGesturesEnabled = true
    }

    private fun renderPlannedRouteIfReady() {
        val map = gmap ?: return
        if (plannedRoute.size < 2) return
        plannedPolyline?.remove()
        wpMarkers.forEach { it.remove() }
        wpMarkers.clear()
        val plannedColor = ContextCompat.getColor(this, R.color.colorOnSurfaceVariant)
        plannedPolyline = map.addPolyline(
            PolylineOptions().addAll(plannedRoute).width(7f).color(plannedColor)
        )
        val wpIconSize = 22f
        for (i in plannedRoute.indices) {
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
                    .position(plannedRoute[i])
                    .title("Punkt ${i + 1}")
                    .icon(icon)
                    .anchor(0.5f, 0.5f)
                    .zIndex(1f)
            )
            if (m != null) wpMarkers.add(m)
        }

        val b = LatLngBounds.builder()
        plannedRoute.forEach { b.include(it) }
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 120))
        val visible = map.cameraPosition.zoom >= waypointVisibleZoom
        wpMarkers.forEach { it.isVisible = visible }
    }

    private fun ensureLivePolyline(map: GoogleMap) {
        if (livePolyline != null) return
        val liveColor = ContextCompat.getColor(this, R.color.colorPrimary)
        livePolyline = map.addPolyline(PolylineOptions().width(9f).color(liveColor))
    }

    private fun updateLiveOnMap(lat: Double, lon: Double, alt: Double?) {
        val map = gmap ?: return
        val pos = LatLng(lat, lon)
        ensureLivePolyline(map)
        val label = if (usePhoneLocation) "T" else "D"
        val fill = if (usePhoneLocation)
            ContextCompat.getColor(this, R.color.colorSurfaceVariant)
        else
            ContextCompat.getColor(this, R.color.colorPrimary)
        val txtColor = if (usePhoneLocation)
            ContextCompat.getColor(this, R.color.colorOnSurface)
        else
            ContextCompat.getColor(this, R.color.colorOnPrimary)
        val icon = makeCircleMarkerIcon(
            sizeDp = 34f,
            fillColor = fill,
            strokeColor = 0,
            strokeDp = 0f,
            text = label,
            textColor = txtColor,
            textSp = 14f,
            bold = true
        )
        if (droneMarker == null) {
            droneMarker = map.addMarker(
                MarkerOptions()
                    .position(pos)
                    .title(if (usePhoneLocation) "Telefon (DEMO)" else "Dron")
                    .icon(icon)
                    .anchor(0.5f, 0.5f)
                    .zIndex(2f)
            )
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
        } else {
            droneMarker?.position = pos
            droneMarker?.setIcon(icon)
        }
        val last = livePath.lastOrNull()
        if (last == null || distanceMeters(last, pos) >= 1.5) {
            livePath.add(pos)
            livePolyline?.points = livePath
        }
        tvPosition.text = buildString {
            append(if (usePhoneLocation) "Pozycja telefonu: " else "Pozycja drona: ")
            append(String.format(Locale("pl", "PL"), "%.6f, %.6f", lat, lon))
            if (alt != null) {
                append("   |   h: ")
                append(String.format(Locale("pl", "PL"), "%.1f m", alt))
            }
        }
    }

    private fun loadPlannedRoute(routeId: Int) {
        lifecycleScope.launch {
            try {
                val rawPoints: List<Map<String, Any?>> = withContext(Dispatchers.IO) {
                    ApiClient.api.getRoutePoints(routeId)
                }
                plannedRoute = rawPoints.mapNotNull { p ->
                    val lon = (p["lon"] as? Number)?.toDouble()
                        ?: (p["lng"] as? Number)?.toDouble()
                        ?: (p["longitude"] as? Number)?.toDouble()

                    val lat = (p["lat"] as? Number)?.toDouble()
                        ?: (p["latitude"] as? Number)?.toDouble()

                    if (lat != null && lon != null) LatLng(lat, lon) else null
                }
                if (plannedRoute.isEmpty()) {
                    Toast.makeText(this@FlightLiveActivity, "Trasa nie ma punktów", Toast.LENGTH_SHORT).show()
                }
                renderPlannedRouteIfReady()
            } catch (e: Exception) {
                Log.e(TAG, "loadPlannedRoute error", e)
                Toast.makeText(this@FlightLiveActivity, "Błąd trasy: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startDjiLocationListener() {
        try {
            val key = KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D)
            KeyManager.getInstance().listen(
                key,
                djiLocationHolder,
                CommonCallbacks.KeyListener<LocationCoordinate3D> { _, newValue ->
                    lastDjiLocation = newValue
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "startDjiLocationListener error", e)
            Toast.makeText(this, "DJI listener error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopDjiLocationListener() {
        try { KeyManager.getInstance().cancelListen(djiLocationHolder) }
        catch (e: Exception) { Log.w(TAG, "stopDjiLocationListener error", e) }
    }

    private fun startDjiFlyingListener() {
        try {
            val key = KeyTools.createKey(FlightControllerKey.KeyIsFlying)
            KeyManager.getInstance().listen(
                key,
                djiFlyingHolder,
                CommonCallbacks.KeyListener<Boolean> { _, newValue ->
                    val isFlyingNow = newValue == true
                    lastIsFlyingNow = isFlyingNow
                    if (isFlyingNow) {
                        if (!flightWasAirborne) {
                            TelemetrySession.addEvent("DRON: wykryto start (KeyIsFlying=true)")
                        }
                        flightWasAirborne = true
                    }
                    if (flightWasAirborne && !isFlyingNow && !landingHandled) {
                        landingHandled = true
                        runOnUiThread {
                            Toast.makeText(this, "Wykryto lądowanie – kończę lot.", Toast.LENGTH_SHORT).show()
                        }
                        onLandingDetected()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "startDjiFlyingListener error", e)
        }
    }

    private fun stopDjiFlyingListener() {
        try { KeyManager.getInstance().cancelListen(djiFlyingHolder) }
        catch (e: Exception) { Log.w(TAG, "stopDjiFlyingListener error", e) }
    }

    private fun onLandingDetected() {
        telemetryUploadEnabled = false
        if (TelemetrySession.isActive()) {
            TelemetrySession.addEvent("MISJA: ZAKOŃCZONA")
            TelemetrySession.addEvent("LĄDOWANIE: wykryte (KeyIsFlying=false)")
            TelemetrySession.stopSession()
        }

        finishFlightAndGoSummary()
    }

    private fun finishFlightAndGoSummary() {
        if (finishSent) return
        finishSent = true
        lifecycleScope.launch {
            if (flightId > 0) {
                try {
                    withContext(Dispatchers.IO) { ApiClient.api.finishFlight(flightId) }
                    Log.i(TAG, "finishFlight OK id=$flightId")
                } catch (e: Exception) {
                    Log.e(TAG, "finishFlight ERROR", e)
                    Toast.makeText(this@FlightLiveActivity, "Błąd zakończenia lotu w DB: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.w(TAG, "Brak flightId – nie kończę w DB")
            }
            goSummary()
        }
    }

    private fun abortFlightAndGoSummary() {
        if (abortSent) return
        abortSent = true
        telemetryUploadEnabled = false
        lifecycleScope.launch {
            if (flightId > 0) {
                try {
                    withContext(Dispatchers.IO) { ApiClient.api.abortFlight(flightId) }
                    Log.i(TAG, "abortFlight OK id=$flightId")
                } catch (e: Exception) {
                    Log.e(TAG, "abortFlight ERROR", e)
                    Toast.makeText(this@FlightLiveActivity, "Błąd przerwania lotu w DB: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.w(TAG, "Brak flightId – nie przerywam w DB")
            }
            goSummary()
        }
    }

    private fun goSummary() {
        runOnUiThread {
            val i = Intent(this, FlightMoreInfoActivity::class.java)
            i.putExtra("flight_id", flightId)
            i.putExtra("route_id", routeId)
            startActivity(i)
            finish()
        }
    }

    private fun uploadTelemetryPoint(lat: Double, lon: Double, alt: Double?) {
        if (!telemetryUploadEnabled) return
        if (flightId <= 0) return
        if (telemetryUploadInProgress) return
        telemetryUploadInProgress = true
        val req = TelemetryCreateRequest(
            lat = lat,
            lon = lon,
            wysokosc_m = alt,
            czas_ms = System.currentTimeMillis()
        )
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ApiClient.api.addFlightTelemetry(flightId, req)
            } catch (e: Exception) {
                Log.w(TAG, "uploadTelemetryPoint failed: ${e.message}")
            } finally {
                telemetryUploadInProgress = false
            }
        }
    }

    private fun startPhoneLocation() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!hasLocPerm()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_LOC
            )
            return
        }

        val mgr = locationManager ?: return
        safeFetchLastKnownLocation(mgr)
        phoneListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lastPhoneLocation = location
            }
            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        safeRequestUpdates(mgr, LocationManager.GPS_PROVIDER)
        safeRequestUpdates(mgr, LocationManager.NETWORK_PROVIDER)
    }

    private fun stopPhoneLocation() {
        val mgr = locationManager
        val l = phoneListener
        if (mgr != null && l != null) {
            try { mgr.removeUpdates(l) } catch (_: Exception) {}
        }
        phoneListener = null
        locationManager = null
    }

    private fun hasLocPerm(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOC) {
            if (hasLocPerm()) startPhoneLocation()
            else Toast.makeText(this, "Brak zgody na lokalizację telefonu", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeFetchLastKnownLocation(mgr: LocationManager) {
        try {
            val last = mgr.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: mgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (last != null) lastPhoneLocation = last
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun safeRequestUpdates(mgr: LocationManager, provider: String) {
        val listener = phoneListener ?: return
        try {
            mgr.requestLocationUpdates(provider, 2000L, 1f, listener)
        } catch (_: Exception) {}
    }

    private fun startUiRefreshLoop() {
        uiRefreshJob?.cancel()
        uiRefreshJob = lifecycleScope.launch {
            while (true) {
                if (usePhoneLocation) {
                    val loc = lastPhoneLocation
                    if (loc != null && isValidLatLon(loc.latitude, loc.longitude)) {
                        val alt = if (loc.hasAltitude()) loc.altitude else null
                        updateLiveOnMap(loc.latitude, loc.longitude, alt)
                        if (TelemetrySession.isActive()) {
                            TelemetrySession.addTelemetry(loc.latitude, loc.longitude, alt, true)
                        }
                        uploadTelemetryPoint(loc.latitude, loc.longitude, alt)
                    }
                } else {
                    val loc = lastDjiLocation
                    if (loc != null && isValidLatLon(loc.latitude, loc.longitude)) {
                        updateLiveOnMap(loc.latitude, loc.longitude, loc.altitude)
                        if (TelemetrySession.isActive()) {
                            TelemetrySession.addTelemetry(loc.latitude, loc.longitude, loc.altitude, lastIsFlyingNow)
                        }
                        uploadTelemetryPoint(loc.latitude, loc.longitude, loc.altitude)
                    }
                }
                delay(UI_REFRESH_MS)
            }
        }
    }

    private fun stopUiRefreshLoop() {
        uiRefreshJob?.cancel()
        uiRefreshJob = null
    }

    private fun startCountdownIfNeeded(plannedTimeS: Int) {
        countdownTimer?.cancel()
        countdownTimer = null
        if (plannedTimeS <= 0) {
            tvRemaining.text = "Pozostały czas: --:--"
            return
        }
        countdownTimer = object : CountDownTimer(plannedTimeS * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = (millisUntilFinished / 1000L).toInt().coerceAtLeast(0)
                tvRemaining.text = "Pozostały czas: ${formatMmSs(sec)}"
            }
            override fun onFinish() {
                tvRemaining.text = "Pozostały czas: 00:00"
            }
        }.start()
    }

    private fun formatMmSs(totalSeconds: Int): String {
        val mm = totalSeconds / 60
        val ss = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mm, ss)
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
        val bmp = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val r = (sizePx / 2f) - strokePx
        val paintFill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = fillColor
        }
        canvas.drawCircle(cx, cy, r, paintFill)
        if (strokeDp > 0f && strokeColor != 0) {
            val paintStroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = strokeColor
                strokeWidth = strokePx
            }
            canvas.drawCircle(cx, cy, r, paintStroke)
        }
        val paintText = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = textSp * density
            typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        }
        val bounds = android.graphics.Rect()
        paintText.getTextBounds(text, 0, text.length, bounds)
        val textY = cy + bounds.height() / 2f
        canvas.drawText(text, cx, textY, paintText)

        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    private fun isValidLatLon(lat: Double, lon: Double): Boolean {
        return lat in MIN_VALID_LAT..MAX_VALID_LAT && lon in MIN_VALID_LON..MAX_VALID_LON
    }

    private fun distanceMeters(a: LatLng, b: LatLng): Double {
        val r = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, r)
        return r[0].toDouble()
    }
}
