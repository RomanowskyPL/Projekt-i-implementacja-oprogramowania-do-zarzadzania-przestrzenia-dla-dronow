package pl.twoja.apka.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import pl.twoja.apka.R
import pl.twoja.apka.model.CertificateItem

class OperatorCertAdapter(
    private var items: List<CertificateItem>,
    private val onOpenPhoto: (CertificateItem) -> Unit
) : RecyclerView.Adapter<OperatorCertAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvCertName)
        val tvIssue: TextView = v.findViewById(R.id.tvIssueDate)
        val tvExpiry: TextView = v.findViewById(R.id.tvExpiryDate)
        val btn: View = v.findViewById(R.id.btnOpenPhoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_certificate, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val cert = items[position]
        h.tvName.text  = cert.nazwa.ifBlank { "(bez nazwy)" }
        h.tvIssue.text = "Data wydania: " + (cert.dataWydania ?: "—")
        h.tvExpiry.text = "Data wygaśnięcia: " + (cert.dataWygasniecia ?: "—")

        val hasPhoto = !cert.photoUrl.isNullOrBlank()
        h.btn.isEnabled = hasPhoto
        h.btn.alpha = if (hasPhoto) 1f else 0.4f
        h.btn.setOnClickListener { if (hasPhoto) onOpenPhoto(cert) }
    }

    override fun getItemCount(): Int = items.size
    fun submit(newItems: List<CertificateItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
