package pl.twoja.apka

import android.content.Intent
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
import pl.twoja.apka.model.CertificateItem
import pl.twoja.apka.ui.OperatorCertAdapter

class OperatorDetailActivity : ComponentActivity() {

    private lateinit var tvImie: TextView
    private lateinit var tvNazwisko: TextView
    private lateinit var tvObywatelstwo: TextView
    private lateinit var tvDataUrodzenia: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvAdres: TextView
    private lateinit var tvTelefon: TextView
    private lateinit var tvNumerOperatora: TextView
    private lateinit var rvCerts: RecyclerView
    private lateinit var certAdapter: OperatorCertAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_operator_detail)
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tvImie = findViewById(R.id.tvImie)
        tvNazwisko = findViewById(R.id.tvNazwisko)
        tvObywatelstwo = findViewById(R.id.tvObywatelstwo)
        tvDataUrodzenia = findViewById(R.id.tvDataUrodzenia)
        tvStatus = findViewById(R.id.tvStatus)
        tvEmail = findViewById(R.id.tvEmail)
        tvAdres = findViewById(R.id.tvAdres)
        tvTelefon = findViewById(R.id.tvTelefon)
        tvNumerOperatora = findViewById(R.id.tvNumerOperatora)
        rvCerts = findViewById(R.id.rvCerts)
        rvCerts.layoutManager = LinearLayoutManager(this)
        certAdapter = OperatorCertAdapter(emptyList()) { cert ->
            if (cert.photoUrl.isNullOrBlank()) {
                Toast.makeText(this, "Brak zdjęcia certyfikatu", Toast.LENGTH_SHORT).show()
            } else {
                val i = Intent(this, PhotoActivity::class.java)
                i.putExtra("url", cert.photoUrl)
                startActivity(i)
            }
        }
        rvCerts.adapter = certAdapter
        findViewById<FloatingActionButton>(R.id.fabBack).setOnClickListener { finish() }
        val operatorId = intent.getIntExtra("id", -1)
        val fallbackName = intent.getStringExtra("name") ?: ""
        val fallbackEmail = intent.getStringExtra("email") ?: ""
        loadData(operatorId, fallbackName, fallbackEmail)
    }

    private fun loadData(id: Int, fallbackName: String, fallbackEmail: String) {
        val parts = fallbackName.split(" ")
        tvImie.text = parts.firstOrNull() ?: ""
        tvNazwisko.text = parts.drop(1).joinToString(" ")
        tvEmail.text = "E-mail: ${if (fallbackEmail.isBlank()) "—" else fallbackEmail}"
        tvTelefon.text = "Telefon: —"
        tvAdres.text = "—"
        tvNumerOperatora.text = "Numer operatora: —"

        lifecycleScope.launch {
            runCatching {
                if (id > 0) ApiClient.api.getOperator(id) else emptyMap<String, Any?>()
            }.onSuccess { map ->
                if (map.isNotEmpty()) {
                    tvImie.text = (map["imie"] ?: tvImie.text).toString()
                    tvNazwisko.text = (map["nazwisko"] ?: tvNazwisko.text).toString()
                    tvObywatelstwo.text = "Obywatelstwo: " + ((map["obywatelstwo"] ?: "—").toString())
                    tvDataUrodzenia.text = "Data urodzin: " + ((map["data_urodzenia"] ?: "—").toString())
                    val numerOp = (map["numer_operatora"] ?: "").toString().trim()
                    tvNumerOperatora.text = "Numer operatora: " + (if (numerOp.isBlank()) "—" else numerOp)
                    tvStatus.text = "Status: " + ((map["status"] ?: "—").toString())
                    tvEmail.text = "E-mail: " + ((map["e_mail"] ?: "—").toString())
                }
            }

            runCatching {
                if (id > 0) ApiClient.api.getOperatorAddress(id) else emptyMap<String, Any?>()
            }.onSuccess { addr ->
                if (addr.isNotEmpty()) {
                    val ulica  = (addr["ulica"] ?: "").toString().trim()
                    val blok   = (addr["numer_bloku"] ?: "").toString().trim()
                    val msc    = (addr["numer_mieszkania"] ?: "").toString().trim()
                    val miasto = (addr["miasto"] ?: "").toString().trim()
                    val kod    = (addr["kod_pocztowy"] ?: "").toString().trim()
                    val kraj   = (addr["panstwo"] ?: "").toString().trim()
                    val telefon = (addr["numer_telefonu"] ?: "").toString().trim()
                    tvTelefon.text = "Telefon: " + (if (telefon.isBlank()) "—" else telefon)
                    val nrCzesci = buildString {
                        if (blok.isNotEmpty()) append(" ").append(blok)
                        if (msc.isNotEmpty()) append("/").append(msc)
                    }
                    val liniaUlica = (ulica + nrCzesci).trim()
                    val partsAddr = listOf(liniaUlica, miasto, kod, kraj).filter { it.isNotBlank() }
                    tvAdres.text = if (partsAddr.isEmpty()) "—" else partsAddr.joinToString(", ")
                } else {
                    tvAdres.text = "—"
                    tvTelefon.text = "Telefon: —"
                }
            }.onFailure {
                tvAdres.text = "—"
                tvTelefon.text = "Telefon: —"
            }
            runCatching {
                if (id > 0) ApiClient.api.getOperatorCertificates(id) else emptyList<Map<String, Any?>>()
            }.onSuccess { list ->
                val mapped = list.map { m ->
                    CertificateItem(
                        id = (m["id_certyfikatu"] as? Number)?.toInt() ?: 0,
                        nazwa = (m["nazwa"] ?: "").toString(),
                        dataWydania = (m["data_wydania"] ?: "")?.toString()?.ifBlank { null },
                        dataWygasniecia = (m["data_wygasniecia"] ?: "")?.toString()?.ifBlank { null },
                        photoUrl = (m["dokument_url"] ?: "")?.toString()?.ifBlank { null }
                    )
                }
                certAdapter.submit(mapped)
            }
        }
    }
}
