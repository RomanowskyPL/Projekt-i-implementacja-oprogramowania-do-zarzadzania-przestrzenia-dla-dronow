package pl.twoja.apka.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import pl.twoja.apka.R
import pl.twoja.apka.model.RouteItem
import java.util.Locale
import kotlin.math.roundToInt

class RouteAdapter(
    private var items: List<RouteItem>,
    private val onMore: (RouteItem) -> Unit
) : RecyclerView.Adapter<RouteAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvTitle)
        val tvMeta: TextView  = v.findViewById(R.id.tvMeta)
        val tvDesc: TextView  = v.findViewById(R.id.tvDesc)
        val btnMore: View     = v.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.tvTitle.text = item.nazwa.ifBlank { "—" }
        val lenStr = item.planowanaDlugoscM
            ?.let { String.format(Locale("pl","PL"), "%.2f m", it) }
            ?: "—"
        val seconds = item.planowanyCzasMin?.let { (it * 60.0).roundToInt() }
        val timeStr = seconds?.let { formatMmSs(it) } ?: "—"
        h.tvMeta.text = "Planowana długość: $lenStr"
        h.tvDesc.text = "Planowany czas: $timeStr"
        h.btnMore.setOnClickListener { onMore(item) }
    }

    override fun getItemCount(): Int = items.size
    fun submit(newItems: List<RouteItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun formatMmSs(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val mm = s / 60
        val ss = s % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mm, ss)
    }
}
