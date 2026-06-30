package com.example.template.feature.chatdetail.quotepicker

import android.graphics.drawable.BitmapDrawable
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.backgroundpattern.BackgroundPatternView
import com.example.components.bubbles.LinkBubbleView
import com.example.components.designsystem.DSIcon
import com.example.template.core.model.Message
import com.example.template.core.model.Persona
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.appSurface01
import com.example.template.core.ui.appSurface02
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * LinkBubble preview для вкладки «Ссылка» в Modal'е. Mock-контент Google Meet (зеркалит V5).
 * Reply-секция бабла берёт фрагмент из [snapshotRange] если есть, иначе — полное превью
 * исходного сообщения через [replyPreviewText].
 */
@Composable
internal fun QuoteModalLinkPreview(
    message: Message,
    senderPersona: Persona?,
    isMine: Boolean,
    snapshotRange: Pair<Int, Int>?,
    draftText: String,
    bottomContentReserve: Dp = 16.dp,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current

    val replySender = remember(isMine, senderPersona) {
        if (isMine) "Вы"
        else senderPersona?.fullName?.takeIf { it.isNotEmpty() } ?: "Собеседник"
    }
    val replyText = remember(snapshotRange, message) {
        snapshotRange?.let { (s, e) -> extractQuote(message, s, e) }
            ?: replyPreviewText(message)
    }
    val scheme = remember(brand, isDark) { brand.linkBubbleColorScheme(isDark) }
    val patternAsset = remember(brand) { brand.backgroundPatternName(1) }
    val patternColorScheme = remember(brand, isDark) {
        brand.backgroundPatternColorScheme(isDark, paletteIndex = 0)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(appSurface01(isDark)),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                BackgroundPatternView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
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
            Spacer(Modifier.height(16.dp))
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    LinkBubbleView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    }
                },
                update = { view ->
                    view.configure(
                        type = LinkBubbleView.BubbleType.MY,
                        message = draftText,
                        title = "Google Meet",
                        description = "Видеовстреча для обсуждения задач и обмена ключевыми обновлениями по текущему проекту",
                        url = "https://meet.google.com/dvz-prtb-xyk",
                        domain = "meet.google.com",
                        labels = null,
                        time = "10:15",
                        sendingState = LinkBubbleView.SendingState.READ,
                        replySender = replySender,
                        replyText = replyText,
                        colorScheme = scheme,
                        showQuoteIcon = snapshotRange != null,
                        quoteIconTint = scheme.replyNameColor,
                    )
                },
            )
            Spacer(Modifier.height(bottomContentReserve))
        }

        // V2 (MODAL_BUTTONS): кнопка-back, прижата к левому краю бабла с 6dp gap'ом
        // и 28dp от низа. MY-бабл имеет marginStart=56dp, так что 6dp слева + 32dp ширина =
        // padding start=18dp от контейнера.
        if (onBack != null) {
            BackIconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 28.dp),
            )
        }
    }
}

@Composable
private fun BackIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val ctx = LocalContext.current
    val bg = appSurface02(isDark)
    val tint = appBasic(isDark, 0.40f)
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(Unit) {
        bitmap = withContext(Dispatchers.IO) {
            (DSIcon.named(ctx, "back", 24f) as? BitmapDrawable)?.bitmap
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                // scaleX=-1 — горизонтальное зеркало иконки back (Figma showed обратное направление).
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { scaleX = -1f },
                colorFilter = ColorFilter.tint(tint),
            )
        } else {
            Spacer(Modifier.size(24.dp))
        }
    }
}
