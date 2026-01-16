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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import pl.twoja.apka.api.ApiClient
import pl.twoja.apka.model.DroneModelItem
import pl.twoja.apka.ui.DroneModelAdapter

class DronesActivity : ComponentActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: DroneModelAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drones)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_drones)
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        rv = findViewById(R.id.rvDroneModels)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = DroneModelAdapter(emptyList()) { model ->
            val i = Intent(this, DroneDetailActivity::class.java)
            i.putExtra("id_modelu", model.id)
            i.putExtra("header", "${model.producent}, ${model.nazwaModelu}")
            i.putExtra("klasa", model.klasaDrona ?: "")
            i.putExtra("masa", model.masaG ?: -1)
            startActivity(i)
        }
        rv.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabBack).setOnClickListener { finish() }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            runCatching { ApiClient.api.listDroneModels() }
                .onSuccess { list ->
                    val mapped = list.map { m ->
                        DroneModelItem(
                            id = (m["id_modelu"] as Number).toInt(),
                            producent = (m["producent"] ?: "").toString(),
                            nazwaModelu = (m["nazwa_modelu"] ?: "").toString(),
                            klasaDrona = (m["klasa_drona"] ?: "")?.toString()?.ifBlank { null },
                            masaG = (m["masa_g"] as? Number)?.toInt(),
                            liczbaEgzemplarzy = (m["liczba_egzemplarzy"] as? Number)?.toInt() ?: 0
                        )
                    }
                    adapter.submit(mapped)
                }
                .onFailure { Toast.makeText(this@DronesActivity, "Błąd pobierania modeli", Toast.LENGTH_SHORT).show() }
        }
    }
}
