package com.example.template.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow

enum class QuotePickerStyle { FULLSCREEN, MODAL_SWIPE, MODAL_STICKY }

val LocalQuotePickerStyle = staticCompositionLocalOf<MutableStateFlow<QuotePickerStyle>> {
    error("LocalQuotePickerStyle not provided")
}

val LocalLinkRenderEnabled = staticCompositionLocalOf<MutableStateFlow<Boolean>> {
    error("LocalLinkRenderEnabled not provided")
}
