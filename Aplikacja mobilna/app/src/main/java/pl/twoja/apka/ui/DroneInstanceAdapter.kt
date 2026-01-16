package pl.twoja.apka.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import pl.twoja.apka.R
import pl.twoja.apka.model.DroneInstanceItem

class DroneInstanceAdapter(
    private var items: List<DroneInstanceItem>,
    private val onMore: (DroneInstanceItem) -> Unit
) : RecyclerView.Adapter<DroneInstanceAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvStatus: TextView = v.findViewById(R.id.tvStatus)
        val tvSerial: TextView = v.findViewById(R.id.tvSerial)
        val tvDate: TextView = v.findViewById(R.id.tvDate)
        val btn: View = v.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_drone_instance, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.tvStatus.text = item.status ?: "—"
        h.tvSerial.text = "Numer seryjny: " + (item.numerSeryjny ?: "—")
        h.tvDate.text = "Data zakupu: " + (item.dataZakupu ?: "—")
        h.btn.setOnClickListener { onMore(item) }
    }

    override fun getItemCount(): Int = items.size
    fun submit(newItems: List<DroneInstanceItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
