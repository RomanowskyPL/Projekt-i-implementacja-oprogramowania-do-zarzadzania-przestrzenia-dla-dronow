package pl.twoja.apka.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import pl.twoja.apka.R
import pl.twoja.apka.model.FlightItem

class FlightAdapter(
    private var items: List<FlightItem>,
    private val onMore: (FlightItem) -> Unit
) : RecyclerView.Adapter<FlightAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvTimes: TextView = v.findViewById(R.id.tvTimes)
        val tvOperator: TextView = v.findViewById(R.id.tvOperator)
        val tvStatus: TextView = v.findViewById(R.id.tvStatus)
        val btnMore: View = v.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_flight, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.tvName.text = item.trasa.ifBlank { "—" }
        val start = item.czasStartu ?: "—"
        val end = item.czasKonca ?: "—"
        h.tvTimes.text = "Start: $start \nLądowanie: $end"
        h.tvOperator.text = "Operator: ${item.operator ?: "—"}"
        h.tvStatus.text = item.status ?: "—"
        h.btnMore.setOnClickListener { _ -> onMore(item) }
    }

    override fun getItemCount(): Int = items.size
    fun submit(newItems: List<FlightItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
