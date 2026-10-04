package com.somood.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.somood.app.databinding.ItemLogBinding

class LogAdapter(private val items: List<LogHelper.LogEntry>) :
    RecyclerView.Adapter<LogAdapter.VH>() {

    class VH(val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.dayText.text = item.date
        holder.binding.minutesText.text = "${toArabic(item.minutes)} دقيقة"
        val percent = ((item.minutes / 180.0) * 100).toInt().coerceIn(0, 100)
        holder.binding.percentText.text = "${toArabic(percent)}٪"
        holder.binding.progressBar.progress = percent
    }

    override fun getItemCount() = items.size

    private fun toArabic(n: Int): String {
        val western = "0123456789"
        val eastern = "٠١٢٣٤٥٦٧٨٩"
        return n.toString().map { c ->
            val idx = western.indexOf(c)
            if (idx >= 0) eastern[idx] else c
        }.joinToString("")
    }
}
