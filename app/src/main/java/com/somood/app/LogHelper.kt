package com.somood.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** سجل الدقايق اللي النت اتقطع فيها، مجمّعة لكل يوم. */
object LogHelper {

    data class LogEntry(val date: String, val minutes: Int)

    private const val PREF = "somood_logs"

    /**
     * بيسجّل جلسة من [startMs] لـ [endMs]، وبيقسّمها على الأيام لو عدّت منتصف الليل.
     */
    fun record(context: Context, startMs: Long, endMs: Long) {
        if (startMs <= 0L || endMs <= startMs) return
        val prefs = prefs(context)
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val edit = prefs.edit()
        val totals = HashMap<String, Int>()

        var cursor = startMs
        var guard = 0
        while (cursor < endMs && guard++ < 400) {
            val nextMidnight = Calendar.getInstance().apply {
                timeInMillis = cursor
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis
            val chunkEnd = minOf(endMs, nextMidnight)
            val minutes = ((chunkEnd - cursor + 30_000L) / 60_000L).toInt() // تقريب لأقرب دقيقة
            if (minutes > 0) {
                val key = keyFormat.format(Date(cursor))
                totals[key] = (totals[key] ?: prefs.getInt(key, 0)) + minutes
            }
            cursor = chunkEnd
        }
        totals.forEach { (k, v) -> edit.putInt(k, v) }
        edit.apply()
    }

    fun todayMinutes(context: Context): Int {
        val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return prefs(context).getInt(key, 0)
    }

    fun getAllLogs(context: Context): List<LogEntry> {
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displayFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        return prefs(context).all
            .filter { it.value is Int }
            .mapNotNull { entry ->
                try {
                    val parsed = keyFormat.parse(entry.key) ?: return@mapNotNull null
                    LogEntry(displayFormat.format(parsed), entry.value as Int)
                } catch (_: Exception) {
                    null
                }
            }
            .sortedByDescending { it.date }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
