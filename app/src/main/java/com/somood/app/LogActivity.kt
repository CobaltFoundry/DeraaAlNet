package com.somood.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.somood.app.databinding.ActivityLogBinding

class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val logs = LogHelper.getAllLogs(this)
        if (logs.isEmpty()) {
            binding.emptyText.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.summaryCard.visibility = View.GONE
        } else {
            binding.emptyText.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            binding.summaryCard.visibility = View.VISIBLE
            binding.totalText.text = Fmt.minutesLabel(logs.sumOf { it.minutes })
            binding.recyclerView.layoutManager = LinearLayoutManager(this)
            binding.recyclerView.adapter = LogAdapter(logs)
        }
    }
}
