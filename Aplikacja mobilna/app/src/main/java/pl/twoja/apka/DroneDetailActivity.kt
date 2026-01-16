package pl.twoja.apka

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import pl.twoja.apka.api.ApiClient
import pl.twoja.apka.model.DroneInstanceItem
import pl.twoja.apka.ui.DroneInstanceAdapter

class DroneDetailActivity : ComponentActivity() {

    private lateinit var tvHeader: TextView
    private lateinit var tvClass: TextView
    private lateinit var tvMass: TextView
    private lateinit var tvCount: TextView
    private lateinit var rv: RecyclerView
    private lateinit var adapter: DroneInstanceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drone_detail)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_drone_detail)
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tvHeader = findViewById(R.id.tvHeader)
        tvClass  = findViewById(R.id.tvClass)
        tvMass   = findViewById(R.id.tvMass)
        tvCount  = findViewById(R.id.tvCount)
        rv = findViewById(R.id.rvInstances)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = DroneInstanceAdapter(emptyList()) { _ ->
            Toast.makeText(this, "Szczegóły egzemplarza (do zrobienia)", Toast.LENGTH_SHORT).show()
        }
        rv.adapter = adapter
        findViewById<FloatingActionButton>(R.id.fabBack).setOnClickListener { finish() }
        val idModelu = intent.getIntExtra("id_modelu", -1)
        tvHeader.text = intent.getStringExtra("header") ?: ""
        tvClass.text  = "Klasa drona: " + (intent.getStringExtra("klasa") ?: "—")
        val masa = intent.getIntExtra("masa", -1)
        tvMass.text   = "Masa: " + if (masa > 0) "$masa g" else "—"
        load(idModelu)
    }

    private fun load(idModelu: Int) {
        lifecycleScope.launch {
            runCatching { ApiClient.api.getDroneModel(idModelu) }.onSuccess { m ->
                tvHeader.text = "${(m["producent"] ?: "").toString()}, ${(m["nazwa_modelu"] ?: "").toString()}"
                tvClass.text  = "Klasa drona: " + ((m["klasa_drona"] ?: "—").toString())
                val g = (m["masa_g"] as? Number)?.toInt()
                tvMass.text   = "Masa: " + (g?.let { "$it g" } ?: "—")
            }
            runCatching { ApiClient.api.getDroneInstances(idModelu) }
                .onSuccess { list ->
                    val mapped = list.map { r ->
                        DroneInstanceItem(
                            id = (r["id_drona"] as Number).toInt(),
                            status = (r["status"] ?: "")?.toString()?.ifBlank { null },
                            numerSeryjny = (r["numer_seryjny"] ?: "")?.toString()?.ifBlank { null },
                            dataZakupu = (r["data_zakupu"] ?: "")?.toString()?.ifBlank { null }
                        )
                    }
                    tvCount.text = "Liczba egzemplarzy: ${mapped.size}"
                    adapter.submit(mapped)
                }
                .onFailure { Toast.makeText(this@DroneDetailActivity, "Błąd pobierania egzemplarzy", Toast.LENGTH_SHORT).show() }
        }
    }
}
