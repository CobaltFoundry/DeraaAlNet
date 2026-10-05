package com.somood.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogHelper {

    data class LogEntry(val date: String, val minutes: Int)

    private const val PREF = "somood_logs"
    private const val KEY_SESSION_START = "session_start"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)

    fun init(context: Context) {
        // nothing heavy
    }

    fun startSession(context: Context) {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_SESSION_START)) {
            prefs.edit().putLong(KEY_SESSION_START, System.currentTimeMillis()).apply()
fun getSessionElapsedMinutes(context: Context): Int {
    val prefs = prefs(context)
    val start = prefs.getLong(KEY_SESSION_START, 0L)
    if (start == 0L) return 0
    return ((System.currentTimeMillis() - start) / 60000).toInt().coerceAtLeast(0)
}
        }
    }

    fun endSession(context: Context) {
        val prefs = prefs(context)
        val start = prefs.getLong(KEY_SESSION_START, 0L)
        if (start == 0L) return

        val minutes = ((System.currentTimeMillis() - start) / 60000).toInt().coerceAtLeast(0)
        prefs.edit().remove(KEY_SESSION_START).apply()

        if (minutes <= 0) return

        val today = dateFormat.format(Date())
        val current = prefs.getInt(today, 0)
        prefs.edit().putInt(today, current + minutes).apply()
    }

    fun getAllLogs(context: Context): List<LogEntry> {
        val prefs = prefs(context)
        val all = prefs.all
        return all
            .filter { it.key != KEY_SESSION_START && it.value is Int }
            .map { LogEntry(displayFormat.format(dateFormat.parse(it.key)!!), it.value as Int) }
            .sortedByDescending { it.date }
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }
}
