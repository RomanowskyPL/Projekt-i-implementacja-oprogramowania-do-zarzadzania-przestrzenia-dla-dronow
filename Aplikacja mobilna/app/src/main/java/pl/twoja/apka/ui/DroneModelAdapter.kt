package pl.twoja.apka.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import pl.twoja.apka.R
import pl.twoja.apka.model.DroneModelItem

class DroneModelAdapter(
    private var items: List<DroneModelItem>,
    private val onOpen: (DroneModelItem) -> Unit
) : RecyclerView.Adapter<DroneModelAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvLine1: TextView = v.findViewById(R.id.tvLine1)
        val tvLine2: TextView = v.findViewById(R.id.tvLine2)
        val tvLine3: TextView = v.findViewById(R.id.tvLine3)
        val btn: View = v.findViewById(R.id.btnOpen)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_drone_model, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val m = items[position]
        h.tvLine1.text = "${m.producent}, ${m.nazwaModelu}"
        h.tvLine2.text = m.klasaDrona ?: "—"
        h.tvLine3.text = (m.masaG?.toString()?.plus(" g")) ?: "—"
        h.btn.setOnClickListener { onOpen(m) }
    }

    override fun getItemCount(): Int = items.size
    fun submit(newItems: List<DroneModelItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
