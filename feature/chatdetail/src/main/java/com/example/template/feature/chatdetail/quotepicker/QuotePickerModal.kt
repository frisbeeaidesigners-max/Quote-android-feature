package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.model.QuoteVariant

@Composable
fun QuotePickerModal(
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    fullText: String,
    initialStart: Int,
    initialEnd: Int,
    draftText: String,
    variant: QuoteVariant,
    linkRender: Boolean,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    onCancelReply: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val density = LocalDensity.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.attributes = window.attributes.apply {
                    blurBehindRadius = with(density) { 30.dp.roundToPx() }
                }
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.setDimAmount(0.2f)
            } else {
                window.setDimAmount(0.4f)
            }
            // Не менять состояние IME при открытии Dialog'а: была открыта → останется,
            // была закрыта → останется. FLAG_ALT_FOCUSABLE_IM позволяет Dialog получать
            // тапы, а IME остаётся прикреплён к окну под ним (MessagePanel EditText).
            // ADJUST_RESIZE — окно Dialog ресайзится под IME, чтобы контент был выше клавы.
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            )
            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
        ) {
            QuoteModalContent(
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
    }
}
