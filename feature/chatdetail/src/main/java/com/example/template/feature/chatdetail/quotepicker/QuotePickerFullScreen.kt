package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import com.example.template.core.model.Message
import com.example.template.core.model.Persona

/**
 * Внутренний enum для дисптача FULLSCREEN-ветки. Не торчит наружу из feature/chatdetail
 * (state-модель `:core:ui` использует только `QuotePickerStyle.FULLSCREEN`).
 */
internal enum class FullScreenVariant { V4, V5 }

/**
 * V3 (Fullscreen) quote picker — inline Compose-overlay в MainActivity ВНЕ AppScaffold.
 * См. подробный комментарий в коммите feature/quote-v3 о причине ОТКАЗА от Compose Dialog
 * (MIUI: statusBarColor/navigationBarColor не применяются в собственном Window Dialog'а).
 *
 * BackHandler установлен ВНУТРИ контента — он имеет доступ к clearSelectionRef для
 * синхронного дисмисса handle-popup'ов.
 *
 * @param linkRenderEnabled true → V5-layout (с SegmentedControl «Ответ/Ссылка»);
 *                          false → V4-layout (без link-tab).
 */
@Composable
fun QuotePickerFullScreen(
    linkRenderEnabled: Boolean,
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    initialStart: Int,
    initialEnd: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    onCancelReply: () -> Unit,
    draftText: String = "",
) {
    val innerVariant = if (linkRenderEnabled) FullScreenVariant.V5 else FullScreenVariant.V4
    when (innerVariant) {
        FullScreenVariant.V4 -> QuoteFullScreenContent(
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
        FullScreenVariant.V5 -> QuoteV5FullScreenContent(
            message = message,
            senderPersona = senderPersona,
            senderAvatar = senderAvatar,
            isMine = isMine,
            initialStart = initialStart,
            initialEnd = initialEnd,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            onCancelReply = onCancelReply,
            draftText = draftText,
        )
    }
}
