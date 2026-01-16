package pl.twoja.apka

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pl.twoja.apka.api.ApiClient
import pl.twoja.apka.model.FlightItem
import pl.twoja.apka.ui.FlightAdapter
import pl.twoja.apka.ui.StartFlightActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FlightsActivity : ComponentActivity() {

    private val scope = MainScope()
    private lateinit var adapter: FlightAdapter
    private val outFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val inFmt1 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    private val inFmt2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_flights)
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<View>(R.id.btnStart).setOnClickListener {
            startActivity(Intent(this, StartFlightActivity::class.java))
        }
        findViewById<View>(R.id.fabBack).setOnClickListener { finish() }
        val rv = findViewById<RecyclerView>(R.id.rvFlights)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = FlightAdapter(emptyList()) { flight ->
            startActivity(Intent(this, FlightDetailActivity::class.java).apply {
                putExtra("flight_id", flight.id)
                putExtra("flight_name", flight.trasa)
            })
        }
        rv.adapter = adapter

        scope.launch {
            runCatching { ApiClient.api.getFlights() }
                .onSuccess { raw ->
                    val mappedWithDate = raw.mapNotNull { m ->
                        val id = (m["id_lotu"] as? Number)?.toInt() ?: return@mapNotNull null
                        val startRaw = m["czas_startu"]?.toString()
                        val startDate = parseDate(startRaw)
                        val item = FlightItem(
                            id = id,
                            trasa = (m["nazwa_trasy"] ?: "").toString(),
                            czasStartu = fmtDate(startRaw),
                            czasKonca = fmtDate(m["czas_konca"]),
                            operator = (m["operator"] ?: "").toString(),
                            status = (m["status"] ?: "").toString()
                        )
                        Pair(item, startDate)
                    }
                    val sorted = mappedWithDate
                        .sortedWith(compareByDescending<Pair<FlightItem, Date?>> { it.second?.time ?: Long.MIN_VALUE })
                        .map { it.first }
                    adapter.submit(sorted)
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun parseDate(s: String?): Date? {
        if (s.isNullOrBlank() || s == "null") return null
        return try {
            inFmt1.parse(s)
        } catch (_: Exception) {
            try {
                inFmt2.parse(s)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun fmtDate(v: Any?): String? {
        if (v == null) return null
        val s = v.toString()
        val d = parseDate(s)
        return if (d != null) outFmt.format(d) else s
    }
}
