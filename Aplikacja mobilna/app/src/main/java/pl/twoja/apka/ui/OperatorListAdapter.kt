package pl.twoja.apka.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import pl.twoja.apka.R
import pl.twoja.apka.model.OperatorItem

class OperatorListAdapter(
    private var items: List<OperatorItem>,
    private val onDetailsClick: (OperatorItem) -> Unit
) : RecyclerView.Adapter<OperatorListAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvEmail: TextView = view.findViewById(R.id.tvEmail)
        val btnDetails: View = view.findViewById(R.id.btnDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_operator, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvName.text = item.fullName.ifBlank { "(bez nazwy)" }
        holder.tvEmail.text = item.email.ifBlank { "—" }
        holder.btnDetails.setOnClickListener { onDetailsClick(item) }
    }

    override fun getItemCount(): Int = items.size
    fun submit(newItems: List<OperatorItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
