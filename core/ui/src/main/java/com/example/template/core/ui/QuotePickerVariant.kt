package com.example.template.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow

enum class QuotePickerVariant { V4, V5 }

val LocalQuotePickerVariant = staticCompositionLocalOf<MutableStateFlow<QuotePickerVariant>> {
    error("LocalQuotePickerVariant not provided")
}
