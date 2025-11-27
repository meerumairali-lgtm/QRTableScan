package com.example.qrscannertable

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ScannedItem(
    val plant: String,
    val hu: String,
    val product: String,
    val qty: Int,
    val mfg: String
)

class ScannedAdapter(private val items: List<ScannedItem>) : RecyclerView.Adapter<ScannedAdapter.VH>() {
    class VH(view: View): RecyclerView.ViewHolder(view) {
        val tvSerial: TextView = view.findViewById(R.id.tvSerial)
        val tvPlant: TextView = view.findViewById(R.id.tvPlant)
        val tvHU: TextView = view.findViewById(R.id.tvHU)
        val tvProduct: TextView = view.findViewById(R.id.tvProduct)
        val tvQty: TextView = view.findViewById(R.id.tvQty)
        val tvMfg: TextView = view.findViewById(R.id.tvMfg)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_scanned, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvSerial.text = (position + 1).toString()
        holder.tvPlant.text = item.plant
        holder.tvHU.text = item.hu
        holder.tvProduct.text = item.product
        holder.tvQty.text = item.qty.toString()
        holder.tvMfg.text = item.mfg
    }

    override fun getItemCount(): Int = items.size
}
