package com.somood.app

/** دوال تنسيق الأرقام والوقت بالعربي. */
object Fmt {
    private const val WESTERN = "0123456789"
    private const val EASTERN = "٠١٢٣٤٥٦٧٨٩"

    fun arabic(s: String): String = buildString(s.length) {
        for (c in s) {
            val i = WESTERN.indexOf(c)
            append(if (i >= 0) EASTERN[i] else c)
        }
    }

    fun arabic(n: Int): String = arabic(n.toString())

    /** ساعات:دقايق:ثواني، مثلًا ١:٠٥:٠٩ */
    fun clock(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return arabic("%d:%02d:%02d".format(h, m, sec))
    }

    /** تطبيق واحد / تطبيقين / ٣ تطبيقات / ١١ تطبيق */
    fun appsLabel(n: Int): String = when {
        n == 1 -> "تطبيق واحد"
        n == 2 -> "تطبيقين"
        n in 3..10 -> "${arabic(n)} تطبيقات"
        else -> "${arabic(n)} تطبيق"
    }

    /** ٤٥ دقيقة / ٢ س و١٥ د */
    fun minutesLabel(minutes: Int): String {
        if (minutes < 60) return "${arabic(minutes)} دقيقة"
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "${arabic(h)} س" else "${arabic(h)} س و${arabic(m)} د"
    }
}
