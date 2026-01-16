package pl.twoja.apka

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import pl.twoja.apka.api.ApiClient
import pl.twoja.apka.model.OperatorItem
import pl.twoja.apka.ui.OperatorListAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton

class OperatorActivity : ComponentActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: OperatorListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_operator)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        rv = findViewById(R.id.rvOperators)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = OperatorListAdapter(emptyList()) { item ->
            val i = Intent(this, OperatorDetailActivity::class.java)
            i.putExtra("id", item.id)
            i.putExtra("name", item.fullName)
            i.putExtra("email", item.email)
            startActivity(i)
        }
        rv.adapter = adapter
        findViewById<FloatingActionButton>(R.id.fabBack).setOnClickListener { finish() }
        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                val rows = ApiClient.api.listOperator()
                val mapped = rows.map { m ->
                    OperatorItem(
                        id = (m["id_operatora"] as? Number)?.toInt() ?: -1,
                        imie = (m["imie"] ?: "").toString(),
                        nazwisko = (m["nazwisko"] ?: "").toString(),
                        email = (m["e_mail"] ?: "").toString()
                    )
                }
                adapter.submit(mapped)
            } catch (_: Exception) {
                adapter.submit(emptyList())
            }
        }
    }
}
