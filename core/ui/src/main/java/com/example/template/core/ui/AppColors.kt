package com.example.template.core.ui

import androidx.compose.ui.graphics.Color

/**
 * Basic colors с альфой — синтезированные под app-isDark (LocalIsDark), а не системный night-mode.
 * DSColors.basicNN читают системный night-mode и для нашего app-toggle темы непригодны.
 */
fun appBasic(isDark: Boolean, alpha: Float): Color =
    (if (isDark) Color.White else Color.Black).copy(alpha = alpha)

/**
 * Surface 01 — фон поверх всего (header в preview, белый dialog-фон).
 * Light: чистый белый. Dark: тёмно-серый (значение подобрано визуально по существующим экранам).
 */
fun appSurface01(isDark: Boolean): Color =
    if (isDark) Color(0xFF1E1E1E) else Color.White

/**
 * Surface 02 — вторичная поверхность (фон iOS-like menu в quote picker).
 */
fun appSurface02(isDark: Boolean): Color =
    if (isDark) Color(0xFF2A2A2A) else Color(0xFFF5F5F5)
