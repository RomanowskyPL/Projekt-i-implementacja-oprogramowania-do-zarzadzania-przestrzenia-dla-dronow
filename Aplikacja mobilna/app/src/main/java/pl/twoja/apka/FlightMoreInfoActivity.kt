package pl.twoja.apka.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.twoja.apka.R
import pl.twoja.apka.api.ApiClient
import pl.twoja.apka.model.FlightDetailItem
import pl.twoja.apka.telemetry.TelemetrySession

class FlightMoreInfoActivity : ComponentActivity() {

    private var flightId: Int = -1
    private var routeId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flight_more_info)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        flightId = intent.getIntExtra("flight_id", -1)
        routeId = intent.getIntExtra("route_id", -1)

        findViewById<View>(R.id.fabBack).setOnClickListener { finish() }

        val logText = TelemetrySession.getFullLogText()
        findViewById<TextView>(R.id.tvTelemetryLog).text =
            if (logText.isBlank()) "Telemetry: (brak danych)" else logText
        if (flightId != -1) {
            loadDataFromBackend()
        } else {
            findViewById<TextView>(R.id.tvHeader).text = "Szczegóły lotu"
        }
    }

    private fun loadDataFromBackend() {
        lifecycleScope.launch {
            try {
                val details: FlightDetailItem = withContext(Dispatchers.IO) {
                    ApiClient.api.getDetails(flightId)
                }
                findViewById<TextView>(R.id.tvHeader).text = details.nazwa ?: "Szczegóły lotu"
            } catch (e: Exception) {
                Toast.makeText(this@FlightMoreInfoActivity, "Błąd: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
