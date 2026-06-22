package com.example.template.core.ui.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Короткий tactile-tick для пользовательских действий. См. `Vibration.md` в корне репо.
 *
 * Через `USAGE_ALARM`, поскольку MIUI фильтрует TOUCH/NOTIFICATION-каналы по системным
 * настройкам, а ALARM всегда вибрирует. Длительность ≈34мс → «щелчок», не «rattle».
 *
 * Требует `android.permission.VIBRATE` в манифесте.
 */
fun performStrongHaptic(context: Context) {
    @Suppress("DEPRECATION")
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    val effect = VibrationEffect.createWaveform(
        longArrayOf(5L, 7L, 10L, 7L, 5L),
        intArrayOf(35, 170, 255, 170, 35),
        -1,
    )
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = android.os.VibrationAttributes.Builder()
                .setUsage(android.os.VibrationAttributes.USAGE_ALARM)
                .build()
            vibrator.vibrate(effect, attrs)
        } else {
            val audioAttrs = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .build()
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, audioAttrs)
        }
    } catch (_: Throwable) {
        // Vibrator может отсутствовать или быть запрещён системой — просто ничего не делаем.
    }
}
