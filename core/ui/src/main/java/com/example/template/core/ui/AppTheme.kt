package com.example.template.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** Temporary hook for testing theme switching (e.g. wired to a debug button). */
val LocalThemeToggle = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun AppTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppBrand provides AppBrand,
        LocalIsDark provides isDark,
    ) {
        content()
    }
}
