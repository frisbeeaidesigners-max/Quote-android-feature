package com.example.template.feature.chatdetail.quotepicker

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.backgroundpattern.BackgroundPatternView
import com.example.components.designsystem.DSIcon
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.appSurface01
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal data class FullScreenMenuItemSpec(
    val label: String,
    val iconName: String,
    val isDanger: Boolean = false,
    val onClick: () -> Unit,
)

internal data class MenuCallbacks(
    val onSelectFragment: () -> Unit,
    val onApply: () -> Unit,
    val onCancelReply: () -> Unit,
    val onBack: () -> Unit,
    val onConfirmQuote: () -> Unit,
    val onClearQuote: () -> Unit,
)

internal fun itemsForState(
    state: QuoteMenuState,
    cb: MenuCallbacks,
): List<FullScreenMenuItemSpec> = when (state) {
    QuoteMenuState.INITIAL -> listOf(
        FullScreenMenuItemSpec("Выбрать фрагмент", "quote-full", onClick = cb.onSelectFragment),
        FullScreenMenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = cb.onCancelReply),
    )
    QuoteMenuState.INITIAL_WITH_QUOTE -> listOf(
        FullScreenMenuItemSpec("Снять выделение", "cancel-quote", onClick = cb.onClearQuote),
        FullScreenMenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = cb.onCancelReply),
    )
    QuoteMenuState.SELECTING -> listOf(
        FullScreenMenuItemSpec("Назад", "back", onClick = cb.onBack),
        FullScreenMenuItemSpec("Цитировать фрагмент", "quote", onClick = cb.onConfirmQuote),
    )
    QuoteMenuState.INITIAL_MINIMAL -> listOf(
        FullScreenMenuItemSpec("Отменить ответ", "delete", isDanger = true, onClick = cb.onCancelReply),
    )
}

internal fun replyPreviewText(message: Message): String = when (message) {
    is Message.Text -> message.body
    is Message.Media -> message.caption?.takeIf { it.isNotEmpty() } ?: "Медиа"
    is Message.Voice -> "Голосовое сообщение"
    is Message.Link -> message.url
    is Message.CallMeet -> "Звонок"
    is Message.System -> "Системное сообщение"
}

// Bundled Roboto TTF из :components — на MIUI FontWeight.Medium без явного font fallback'ится
// на Mi Sans Regular, и Medium-вес визуально не виден.
internal val FullScreenRobotoFontFamily: FontFamily = FontFamily(
    Font(com.example.components.R.font.roboto, FontWeight.Normal),
    Font(com.example.components.R.font.roboto, FontWeight.Medium),
)

@Composable
internal fun FullScreenMenuRow(
    item: FullScreenMenuItemSpec,
    paddingStart: Dp = 24.dp,
    paddingEnd: Dp = 16.dp,
) {
    val isDark = LocalIsDark.current
    val labelColor =
        if (item.isDanger) Color(0xFFE06141)
        else appBasic(isDark, 0.9f)
    val iconColor =
        if (item.isDanger) Color(0xFFE06141)
        else appBasic(isDark, 0.55f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rowBg = if (isPressed) appBasic(isDark, 0.08f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(rowBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = item.onClick,
            )
            .padding(start = paddingStart, end = paddingEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DsIconImage(name = item.iconName, tint = iconColor, sizeDp = 24)
        Spacer(Modifier.width(24.dp))
        Text(
            text = item.label,
            color = labelColor,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontFamily = FullScreenRobotoFontFamily,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
internal fun DsIconImage(name: String, tint: Color, sizeDp: Int = 24) {
    val ctx = LocalContext.current
    var bitmap by remember(name) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(name) {
        bitmap = withContext(Dispatchers.IO) {
            (DSIcon.named(ctx, name, sizeDp.toFloat()) as? BitmapDrawable)?.bitmap
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(sizeDp.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    } else {
        Spacer(Modifier.size(sizeDp.dp))
    }
}

@Composable
internal fun PreviewArea(
    message: Message,
    senderPersona: Persona?,
    senderAvatar: Bitmap?,
    isMine: Boolean,
    initialStart: Int,
    initialEnd: Int,
    tvRef: MutableState<TextView?>,
    selectAllRef: MutableState<(() -> Unit)?>,
    selectionRef: MutableState<(() -> IntRange?)?>,
    clearSelectionRef: MutableState<(() -> Unit)?>,
    onSelectionStart: () -> Unit,
    onSelectionEnd: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    bottomSpacerDp: Dp = 16.dp,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current

    val patternAsset = remember(brand) { brand.backgroundPatternName(1) }
    val patternColorScheme = remember(brand, isDark) {
        brand.backgroundPatternColorScheme(isDark, paletteIndex = 0)
    }
    val previewBg = appSurface01(isDark)

    val previewHandleClipRect = remember { mutableStateOf<android.graphics.Rect?>(null) }
    val previewMenuClipRect = remember { mutableStateOf<android.graphics.Rect?>(null) }
    val density = LocalDensity.current
    val handleOverflowPx = with(density) { 24.dp.roundToPx() }

    Box(
        modifier = modifier
            .background(previewBg)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val size = coords.size
                val x = pos.x.toInt()
                val y = pos.y.toInt()
                val menuR = android.graphics.Rect(x, y, x + size.width, y + size.height)
                val handleR = android.graphics.Rect(x, y, x + size.width, y + size.height + handleOverflowPx)
                if (previewMenuClipRect.value != menuR) previewMenuClipRect.value = menuR
                if (previewHandleClipRect.value != handleR) previewHandleClipRect.value = handleR
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                BackgroundPatternView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    configure(patternAsset, patternColorScheme)
                }
            },
            update = { v -> v.configure(patternAsset, patternColorScheme) },
        )

        val scrollState = rememberScrollState()
        LaunchedEffect(message.id) {
            snapshotFlow { scrollState.maxValue }
                .first { it > 0 }
                .let { scrollState.scrollTo(it) }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Spacer(Modifier.height(8.dp))
            QuoteBubblePreview(
                message = message,
                senderPersona = senderPersona,
                senderAvatar = senderAvatar,
                isMine = isMine,
                tvRef = tvRef,
                onQuoteFromActionMode = onConfirm,
                selectAllRef = selectAllRef,
                selectionRef = selectionRef,
                clearSelectionRef = clearSelectionRef,
                onSelectionStart = onSelectionStart,
                onSelectionEnd = onSelectionEnd,
                onCopyFromActionMode = onDismiss,
                initialSelectionStart = initialStart,
                initialSelectionEnd = initialEnd,
                previewScrollState = scrollState,
                previewClipRect = previewHandleClipRect.value,
                previewMenuClipRect = previewMenuClipRect.value,
                previewScrollInProgress = scrollState.isScrollInProgress,
            )
            Spacer(Modifier.height(bottomSpacerDp))
        }
    }
}
