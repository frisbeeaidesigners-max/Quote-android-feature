package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appSurface01

@Composable
fun QuoteV5FullScreenContent(
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
    val isDark = LocalIsDark.current

    // FSM init — то же правило, что у V4 (QuoteFullScreenContent.kt:87-100).
    val hasSelectableText = when (message) {
        is Message.Text -> message.body.isNotEmpty()
        is Message.Media -> !message.caption.isNullOrEmpty()
        else -> false
    }
    var menuState by rememberSaveable {
        mutableStateOf(
            when {
                !hasSelectableText -> QuoteMenuState.INITIAL_MINIMAL
                initialStart < initialEnd -> QuoteMenuState.INITIAL_WITH_QUOTE
                else -> QuoteMenuState.INITIAL
            }
        )
    }

    // Selection refs — те же, что у V4 (QuoteFullScreenContent.kt:103-106).
    val tvRef = remember { mutableStateOf<TextView?>(null) }
    val selectAllRef = remember { mutableStateOf<(() -> Unit)?>(null) }
    val selectionRef = remember { mutableStateOf<(() -> IntRange?)?>(null) }
    val clearSelectionRef = remember { mutableStateOf<(() -> Unit)?>(null) }

    BackHandler {
        clearSelectionRef.value?.invoke()
        onDismiss()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appSurface01(isDark))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}
