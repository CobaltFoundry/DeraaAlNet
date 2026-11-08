package com.somood.app

import android.content.Context

/**
 * حالة "النت مقطوع" المحفوظة على القرص.
 * ده المصدر الوحيد للحقيقة: الشاشة والخدمة والـ BootReceiver كلهم بيقروا منه،
 * فالحالة بتفضل صح حتى لو العملية اتقتلت أو الجهاز اتعمله restart.
 */
object SessionState {
    private const val PREF = "somood_state"
    private const val KEY_ACTIVE = "active"
    private const val KEY_STARTED_AT = "started_at"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun isActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ACTIVE, false)

    fun startedAt(context: Context): Long =
        prefs(context).getLong(KEY_STARTED_AT, 0L)

    /** بداية جلسة جديدة. لو فيه جلسة شغالة أصلًا مبنغيّرش وقت بدايتها. */
    fun begin(context: Context) {
        if (isActive(context)) return
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_STARTED_AT, System.currentTimeMillis())
            .apply()
    }

    /** بعد إعادة التشغيل: الجهاز كان مقفول، فنبدأ العدّ من دلوقتي بدل ما نحسب وقت الإغلاق. */
    fun restartClock(context: Context) {
        if (!isActive(context)) return
        prefs(context).edit().putLong(KEY_STARTED_AT, System.currentTimeMillis()).apply()
    }

    /** نهاية الجلسة. بترجّع وقت بدايتها (أو 0 لو مكانش فيه جلسة). */
    fun end(context: Context): Long {
        val started = if (isActive(context)) startedAt(context) else 0L
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, false)
            .putLong(KEY_STARTED_AT, 0L)
            .apply()
        return started
    }
}
