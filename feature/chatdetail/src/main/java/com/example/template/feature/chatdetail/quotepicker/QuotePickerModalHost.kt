package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.model.QuoteVariant
import com.example.template.core.ui.QuotePickerStyle

/**
 * Хост Modal-picker'а. Маппит [QuotePickerStyle] (из :core:ui — наружу торчит для
 * Profile/AppContainer) в [QuoteVariant] (из :core:model — внутри Modal-кода).
 * Принимает только MODAL_STICKY_2/MODAL_STICKY/MODAL_BUTTONS; FULLSCREEN-диспатч в
 * [QuotePickerFullScreen].
 */
@Composable
fun QuotePickerModalHost(
    style: QuotePickerStyle,
    linkRender: Boolean,
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    fullText: String,
    initialStart: Int,
    initialEnd: Int,
    draftText: String,
    onConfirm: (start: Int, end: Int) -> Unit,
    onDismiss: () -> Unit,
    onCancelReply: () -> Unit,
) {
    val variant = when (style) {
        QuotePickerStyle.MODAL_STICKY_2 -> QuoteVariant.MODAL_STICKY_2
        QuotePickerStyle.MODAL_STICKY -> QuoteVariant.MODAL_STICKY
        QuotePickerStyle.MODAL_BUTTONS -> QuoteVariant.MODAL_BUTTONS
        QuotePickerStyle.FULLSCREEN -> error(
            "QuotePickerModalHost called with FULLSCREEN style; use QuotePickerFullScreen instead"
        )
    }
    QuotePickerModal(
        message = message,
        senderPersona = senderPersona,
        senderAvatar = senderAvatar,
        isMine = isMine,
        fullText = fullText,
        initialStart = initialStart,
        initialEnd = initialEnd,
        draftText = draftText,
        variant = variant,
        linkRender = linkRender,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        onCancelReply = onCancelReply,
    )
}
