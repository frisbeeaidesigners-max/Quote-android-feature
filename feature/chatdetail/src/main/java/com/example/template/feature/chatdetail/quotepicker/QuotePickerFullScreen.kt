package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.ui.QuotePickerVariant

/**
 * V3 (Fullscreen) quote picker. Рендерится как inline Compose-overlay в MainActivity ВНЕ
 * AppScaffold (по тому же pattern что [ContextMenuOverlay][com.example.template.feature.chatdetail.ContextMenuOverlay]),
 * НЕ через `androidx.compose.ui.window.Dialog`.
 *
 * Почему не Dialog: Compose Dialog с usePlatformDefaultWidth=false + decorFitsSystemWindows=false
 * создаёт собственное Window, чьи `statusBarColor`/`navigationBarColor`/`isAppearanceLight*Bars`
 * на ряде ROM'ов (MIUI) не применяются — иконки часов / wifi оказываются дефолтными (light)
 * и не видны на light theme, а Dialog рисуется поверх области nav-bar. Activity-окно уже
 * настроено в `MainActivity.onCreate` (enableEdgeToEdge + isStatusBarContrastEnforced=false +
 * isAppearanceLight*Bars=!isDark) → если рендерить overlay в этом же окне, всё работает.
 *
 * Контент ([QuoteFullScreenContent]) сам делает `.fillMaxSize().background(appSurface01)
 * .statusBarsPadding().navigationBarsPadding()` — то есть фон тянется edge-to-edge,
 * а контент инсетится внутри безопасной области.
 */
@Composable
fun QuotePickerFullScreen(
    variant: QuotePickerVariant,
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    onCancelReply: () -> Unit,
) {
    // BackHandler установлен ВНУТРИ контента (QuoteFullScreenContent / QuoteV5FullScreenContent) —
    // он имеет доступ к clearSelectionRef для синхронного дисмисса handle-popup'ов.
    when (variant) {
        QuotePickerVariant.V4 -> QuoteFullScreenContent(
            message = message,
            senderPersona = senderPersona,
            senderAvatar = senderAvatar,
            isMine = isMine,
            initialStart = initialStart,
            initialEnd = initialEnd,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            onCancelReply = onCancelReply,
        )
        QuotePickerVariant.V5 -> QuoteV5FullScreenContent(
            message = message,
            senderPersona = senderPersona,
            senderAvatar = senderAvatar,
            isMine = isMine,
            initialStart = initialStart,
            initialEnd = initialEnd,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            onCancelReply = onCancelReply,
        )
    }
}
