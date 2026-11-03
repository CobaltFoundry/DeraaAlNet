package com.somood.app

import android.content.Context

object DurationHelper {
    private const val PREF = "somood_duration"
    private const val KEY_MINUTES = "selected_minutes"
    private const val KEY_END_AT = "session_end_at"

    fun getSelectedMinutes(context: Context): Int {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_MINUTES, 60)
    }

    fun setSelectedMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MINUTES, minutes)
            .apply()
    }

    fun setSessionEndAt(context: Context, endAtMillis: Long) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_END_AT, endAtMillis)
            .apply()
    }

    fun getSessionEndAt(context: Context): Long {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getLong(KEY_END_AT, 0L)
    }

    fun clearSessionEnd(context: Context) {
        setSessionEndAt(context, 0L)
    }

    fun getRemainingMinutes(context: Context): Int {
        val end = getSessionEndAt(context)
        if (end <= 0L) return -1
        val left = ((end - System.currentTimeMillis()) / 60000).toInt()
        return left.coerceAtLeast(0)
    }
}