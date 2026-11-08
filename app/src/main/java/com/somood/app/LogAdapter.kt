package com.somood.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.somood.app.databinding.ItemLogBinding

class LogAdapter(private val items: List<LogHelper.LogEntry>) :
    RecyclerView.Adapter<LogAdapter.VH>() {

    class VH(val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root)

    // الشريط نسبةً لأطول يوم، والنسبة المكتوبة = حصة اليوم (٢٤ ساعة) اللي النت كان فيها مقطوع
    private val longest = (items.maxOfOrNull { it.minutes } ?: 1).coerceAtLeast(1)

    private val today = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US)
        .format(java.util.Date())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.dayText.text =
            Fmt.arabic(item.date) + if (item.date == today) " — النهارده" else ""
        holder.binding.minutesText.text = Fmt.minutesLabel(item.minutes)
        val ofDay = (item.minutes * 100 / 1440).coerceIn(0, 100)
        holder.binding.percentText.text = "${Fmt.arabic(ofDay)}٪ من اليوم"
        holder.binding.progressBar.progress = (item.minutes * 100 / longest).coerceIn(0, 100)
    }

    override fun getItemCount() = items.size
}
