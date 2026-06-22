package com.example.template.core.ui

import android.util.Log

/**
 * Замер задержек при первом открытии чата. `reset()` в момент тапа, `mark(tag)` — где угодно
 * по пути инфляции/композиции. Лог через `adb logcat -s TIMING:D`.
 */
object Timing {
    @Volatile private var t0Nanos: Long = 0L

    fun reset() {
        t0Nanos = System.nanoTime()
        Log.d("TIMING", "+0ms RESET")
    }

    fun mark(tag: String) {
        val t0 = t0Nanos
        if (t0 == 0L) return
        val elapsed = (System.nanoTime() - t0) / 1_000_000
        Log.d("TIMING", "+${elapsed}ms $tag")
    }
}
