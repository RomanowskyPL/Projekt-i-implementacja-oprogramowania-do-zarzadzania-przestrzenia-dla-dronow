package pl.twoja.apka

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import pl.twoja.apka.api.ApiClient
import pl.twoja.apka.model.RouteItem
import pl.twoja.apka.ui.RouteAdapter
import kotlin.math.roundToInt

class RoutesActivity : ComponentActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: RouteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_routes)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        rv = findViewById(R.id.rvRoutes)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = RouteAdapter(emptyList()) { route ->
            val timeSec = route.planowanyCzasMin?.let { (it * 60.0).roundToInt() } ?: -1

            startActivity(Intent(this, RouteDetailActivity::class.java).apply {
                putExtra("id", route.id)
                putExtra("name", route.nazwa)
                putExtra("len", route.planowanaDlugoscM ?: -1.0)
                putExtra("time_s", timeSec)
                putExtra("opis", route.opis ?: "")
            })
        }
        rv.adapter = adapter
        findViewById<View>(R.id.fabBack)?.setOnClickListener { finish() }
        lifecycleScope.launch {
            runCatching { ApiClient.api.getRoutes() }
                .onSuccess { raw ->
                    val mapped = raw.mapNotNull { m ->
                        val id = (m["id_trasy"] as? Number)?.toInt() ?: return@mapNotNull null
                        val timeMin = (m["planowany_czas_min"] as? Number)?.toDouble()
                            ?: m["planowany_czas_min"]?.toString()?.toDoubleOrNull()
                        RouteItem(
                            id = id,
                            nazwa = (m["nazwa"] ?: "").toString(),
                            opis = m["opis"] as? String,
                            planowanaDlugoscM = (m["planowana_dlugosc_m"] as? Number)?.toDouble(),
                            planowanyCzasMin = timeMin,
                            utworzono = m["utworzono"]?.toString(),
                            pointsCount = (m["points_count"] as? Number)?.toInt() ?: 0
                        )
                    }
                    adapter.submit(mapped)
                }
                .onFailure { e ->
                    Toast.makeText(
                        this@RoutesActivity,
                        "Nie udało się pobrać tras: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }
}
