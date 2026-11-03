package com.somood.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.somood.app.databinding.ItemAppBinding

class AppsAdapter(
    private var items: MutableList<AppsActivity.AppItem>
) : RecyclerView.Adapter<AppsAdapter.VH>() {

    class VH(val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.b.name.text = item.name
        holder.b.icon.setImageDrawable(item.icon)
        holder.b.check.setOnCheckedChangeListener(null)
        holder.b.check.isChecked = item.allowed
        holder.b.check.setOnCheckedChangeListener { _, checked ->
            item.allowed = checked
            // تحديث النسخة الأصلية في القائمة الكاملة عن طريق packageName
            items.find { it.packageName == item.packageName }?.allowed = checked
        }
        holder.itemView.setOnClickListener {
            holder.b.check.isChecked = !holder.b.check.isChecked
        }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<AppsActivity.AppItem>) {
        items = newItems.toMutableList()
        notifyDataSetChanged()
    }

    fun currentItems(): List<AppsActivity.AppItem> = items
}