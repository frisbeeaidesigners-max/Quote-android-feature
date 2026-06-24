package com.example.template.core.ui.hosts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.button.ButtonType
import com.example.components.messagepanel.AttachmentItem
import com.example.components.messagepanel.AttachmentType
import com.example.components.messagepanel.MessagePanelConfig
import com.example.components.messagepanel.MessagePanelView
import com.example.components.messagepanel.ContextBlock
import com.example.template.core.model.MediaAttachment
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.model.AttachmentType as DomainAttachmentType
import com.example.template.core.ui.utils.findMessageTextView

data class ReplyDisplay(
    val authorName: String,
    val previewText: String,
    val isQuote: Boolean = false,
    val canQuote: Boolean = false,
)

data class EditDisplay(
    val originalId: String,
    val previewText: String,
    val initialBody: String,
    val initialAttachments: List<MediaAttachment>,
)

@Composable
fun MessagePanelHost(
    onSendText: (String) -> Unit,
    onSendMedia: (attachments: List<MediaAttachment>, caption: String?) -> Unit = { _, _ -> },
    onSendVoice: (durationMs: Long) -> Unit = {},
    replyContext: ReplyDisplay? = null,
    onReplyClose: () -> Unit = {},
    onReplyClick: () -> Unit = {},
    editContext: EditDisplay? = null,
    onEditClose: () -> Unit = {},
    onSaveEdit: (text: String, attachments: List<MediaAttachment>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    labels: List<String> = DEFAULT_LABELS,
    onPanelReady: ((MessagePanelView) -> Unit)? = null,
    onVoiceModeChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current

    // Стикеры / гифки лениво грузятся из assets и кэшируются на жизнь композиции.
    // Пути синкаются в app/src/main/assets/{stickers,gifs} из ../android-components тасками
    // sync* в app/build.gradle.kts. См. MessagePanelPreviewScreen.kt из gallery — там тот же
    // набор: 15 PNG в pack1 + 14 PNG во втором паке + 9 анимированных webp.
    val stickerImages: List<Bitmap> = remember(context) {
        (1..15).mapNotNull { i -> loadAssetBitmap(context, "stickers/pack1/%02d.png".format(i)) }
    }
    val foxtrotManStickers: List<Bitmap> = remember(context) {
        (1..14).mapNotNull { i -> loadAssetBitmap(context, "stickers/pack2/%02d.png".format(i)) }
    }
    val gifDrawables: List<Drawable> = remember(context) {
        (1..9).mapNotNull { i -> loadAnimatedAsset(context, "gifs/$i.webp") }
    }

    // Демо-вложения для проверки attach UX. Хост сам управляет состоянием так же, как gallery:
    // тап по скрепке при пустом списке восстанавливает дефолтный набор и раскрывает секцию,
    // тап по «крестику» на конкретном thumbnail удаляет его.
    val initialAttachments: List<AttachmentItem> = remember(context) {
        listOf(
            AttachmentItem("0", Uri.EMPTY, AttachmentType.IMAGE, loadAssetBitmap(context, "images/media_01.jpg")),
            AttachmentItem("1", Uri.EMPTY, AttachmentType.IMAGE, loadAssetBitmap(context, "images/media_03.jpg")),
            AttachmentItem("2", Uri.EMPTY, AttachmentType.VIDEO, loadAssetBitmap(context, "images/media_05.jpg"), "1:23"),
            AttachmentItem("3", Uri.EMPTY, AttachmentType.IMAGE, loadAssetBitmap(context, "images/media_07.jpg")),
            AttachmentItem("4", Uri.EMPTY, AttachmentType.IMAGE, loadAssetBitmap(context, "images/media_09.jpg")),
        )
    }
    var attachments by remember { mutableStateOf<List<AttachmentItem>>(emptyList()) }
    var voiceMode by remember { mutableStateOf(false) }
    // Длительность голосового считаем у себя по wall-clock: panel.onSendVoice — () -> Unit,
    // не пробрасывает время. Берём elapsedRealtime (не System.currentTimeMillis) — иммунно
    // к ручным правкам системного времени. Точность совпадает с panel.voiceTickRunnable
    // (та же монотонная шкала), поэтому duration в баббле совпадает с last value таймера.
    var voiceStartElapsedMs by remember { mutableStateOf(0L) }

    // Эмитим voiceMode наружу: внешние слои (MainActivity) перекрашивают системный
    // navigation bar в акцент. rememberUpdatedState — чтобы callback не пересоздавал
    // эффекты при пересборках без смены ссылки. DisposableEffect сбрасывает в false
    // при unmount (закрытие чата), иначе акцент-полоса осталась бы висеть.
    // Сам callback вызываем СИНХРОННО в click-handler'ах ниже (panel.onMicClick и т.д.):
    // через LaunchedEffect(voiceMode) приходила задержка в 1 кадр — эффект исполняется
    // после composition apply, и MainActivity рекомпозился на следующий фрейм, а
    // panel.startVoiceRecording() в AndroidView.update обновлял панель на текущем →
    // полоса визуально подъезжала после.
    val voiceModeCallback by rememberUpdatedState(onVoiceModeChange)
    DisposableEffect(Unit) { onDispose { voiceModeCallback(false) } }

    // Prefill / reset attachments state при entry/exit edit-mode. На entry — берём
    // initialAttachments из edit-контекста; на exit (editContext == null) — сбрасываем в emptyList,
    // иначе следующий reply / новая отправка унаследовал бы чужой набор.
    LaunchedEffect(editContext?.originalId) {
        attachments = editContext?.initialAttachments?.mapIndexed { i, it -> it.toAttachmentItem(i) } ?: emptyList()
    }

    val config = remember(brand, isDark, attachments, labels, stickerImages, foxtrotManStickers, gifDrawables, replyContext, editContext) {
        MessagePanelConfig(
            attachments = attachments,
            labels = labels,
            attachedMediaColorScheme = brand.attachedMediaColorScheme(isDark),
            segmentedControlColorScheme = brand.segmentedControlColorScheme(isDark),
            brandAccentColor = brand.accentColor(isDark),
            stickerImages = stickerImages,
            additionalStickerPacks = listOf("FoxtrotMan" to foxtrotManStickers),
            gifDrawables = gifDrawables,
            stickerAddButtonColorScheme = brand.buttonColorScheme(ButtonType.SECONDARY, isDark),
            // Edit имеет приоритет над reply (mutual exclusion через VM, но guard здесь для надёжности).
            // Если в reply есть quote (isQuote=true) → ContextBlock.Quote, иначе обычный Reply.
            contextBlock = when {
                editContext != null -> ContextBlock.Edit(preview = editContext.previewText)
                replyContext != null && replyContext.isQuote -> ContextBlock.Quote(
                    author = replyContext.authorName,
                    preview = replyContext.previewText,
                    showThumbnail = false,
                )
                replyContext != null -> ContextBlock.Reply(
                    author = replyContext.authorName,
                    preview = replyContext.previewText,
                    showThumbnail = false,
                    iconName = if (replyContext.canQuote) "reply-options" else "reply",
                )
                else -> ContextBlock.None
            },
        )
    }
    val colorScheme = remember(brand, isDark) { brand.messagePanelColorScheme(isDark) }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            MessagePanelView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                onPanelReady?.invoke(this)
            }
        },
        update = { panel ->
            panel.onContextClose = {
                if (editContext != null) {
                    panel.clear()
                    onEditClose()
                } else {
                    onReplyClose()
                }
            }
            panel.configure(config, colorScheme)
            // Reply-block clickability через view-walk. Fragile — см. BubbleTextProbe.kt § note.
            val previewText = replyContext?.previewText
            if (previewText != null && previewText.isNotEmpty()) {
                val tv = panel.findMessageTextView(previewText)
                val container = tv?.parent as? android.view.ViewGroup
                container?.setOnClickListener { onReplyClick() }
            }
            // Fix A: В edit-mode Media держим секцию вложений раскрытой. expandAttachments()
            // guard'ит на currentAttachments.isNotEmpty() внутри :components, так что safe
            // вызывать на каждый recomposition: если уже expanded или attachments пуст — no-op.
            if (editContext != null && attachments.isNotEmpty()) {
                panel.expandAttachments()
            }
            panel.onSendText = { text ->
                if (editContext != null) {
                    // В edit-mode VM сама проверит empty + no-op'нет.
                    onSaveEdit(text, attachments.map { it.toDomain() })
                    panel.clear()
                } else {
                    val pending = attachments
                    if (pending.isEmpty()) {
                        onSendText(text)
                    } else {
                        // Есть приаттаченные файлы → отправляем как Media-сообщение,
                        // текст идёт как caption (может быть пустым). Маппим
                        // AttachmentItem (модель компоненты) в MediaAttachment (наш domain):
                        // бимапы и duration-строки сохраняем как @Transient — переживают
                        // только in-memory сессию (см. MediaAttachment в core.model).
                        val media = pending.map { it.toDomain() }
                        val caption = text.takeIf { it.isNotBlank() }
                        onSendMedia(media, caption)
                        attachments = emptyList()
                    }
                    panel.clear()
                }
            }
            panel.onMicClick = {
                voiceMode = true
                voiceModeCallback(true)
                voiceStartElapsedMs = android.os.SystemClock.elapsedRealtime()
                // IME оставлять открытой бессмысленно — input скрыт в voice-режиме.
                // Сам компонент не прячет клавиатуру в startVoiceRecording (только переключает
                // visibility'ы View'шек). Воспользуемся публичным методом dismissKeyboard,
                // тем же, что MessagePanelView юзает на outside-tap.
                panel.dismissKeyboard()
            }
            panel.onDeleteVoice = { voiceMode = false; voiceModeCallback(false) }
            panel.onSendVoice = {
                val durationMs = android.os.SystemClock.elapsedRealtime() - voiceStartElapsedMs
                voiceMode = false
                voiceModeCallback(false)
                if (durationMs > 0L) onSendVoice(durationMs)
            }
            panel.onAttachmentRemove = { id ->
                attachments = attachments.filterNot { it.id == id }
            }
            panel.onAttachClick = {
                // Эмулируем результат пикера: если ничего не прикреплено — восстанавливаем
                // дефолтный набор. Раскрытие секции компонент делает сам: его
                // `attachButton.onClick` запоминает, что список был пуст до вызова callback,
                // и после следующего configure (когда новые attachments дойдут) раскрывает
                // через pendingExpandAttachments внутри MessagePanelView.
                // Fix B: В edit-mode паперскрепка не дофилллит дефолты — пользователь
                // редактирует существующее сообщение, ему не нужны 5 demo-файлов.
                if (editContext == null && attachments.isEmpty()) {
                    attachments = initialAttachments
                }
            }
            if (voiceMode) panel.startVoiceRecording() else panel.stopVoiceRecording()
        },
    )
}

private val DEFAULT_LABELS = listOf("Важно", "Обсудить", "Готово")

private fun AttachmentItem.toDomain(): MediaAttachment {
    val domainType = when (type) {
        AttachmentType.IMAGE -> DomainAttachmentType.Photo
        AttachmentType.VIDEO -> DomainAttachmentType.Video
    }
    return MediaAttachment(
        // Фоллбек-цвет на случай, если thumbnail не дойдёт до bubble'а (нагрузка
        // на cache etc). Серый «no-image».
        placeholderColor = "#80808080",
        type = domainType,
        durationMs = null,
        thumbnail = thumbnail,
        durationLabel = duration.takeIf { it.isNotBlank() },
    )
}

// Inverse of AttachmentItem.toDomain — для prefill panel'а attachments из edited Media-сообщения.
// Domain не хранит Uri (нужна только для рендера компоненте, mock без реальных Uri) и id —
// синтезируем id из placeholderColor+type+index. Index важен: одинаковые attachments
// (две Photo с одним placeholderColor) дают совпадающие id без него, и `filterNot { it.id == id }`
// в onAttachmentRemove сносит все совпадения сразу. AttachmentType.File мапим в IMAGE.
private fun MediaAttachment.toAttachmentItem(index: Int): AttachmentItem = AttachmentItem(
    id = "${placeholderColor}_${type.name}_$index",
    uri = android.net.Uri.EMPTY,
    type = when (type) {
        DomainAttachmentType.Photo -> AttachmentType.IMAGE
        DomainAttachmentType.Video -> AttachmentType.VIDEO
        DomainAttachmentType.File -> AttachmentType.IMAGE
    },
    thumbnail = thumbnail as? android.graphics.Bitmap,
    duration = durationLabel.orEmpty(),
)

private fun loadAssetBitmap(context: Context, path: String): Bitmap? = runCatching {
    context.assets.open(path).use { BitmapFactory.decodeStream(it) }
}.getOrNull()

/**
 * Декодирует asset как Drawable. На API 28+ возвращает [AnimatedImageDrawable] для
 * анимированных WebP/GIF (auto-started, infinite repeat); на старых API — статичный
 * первый кадр через [BitmapDrawable]. Скопировано один-в-один из gallery PreviewComponents.kt.
 */
private fun loadAnimatedAsset(context: Context, path: String): Drawable? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val src = ImageDecoder.createSource(context.assets, path)
        ImageDecoder.decodeDrawable(src).also { d ->
            if (d is AnimatedImageDrawable) {
                d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                d.start()
            }
        }
    } else {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            ?.let { BitmapDrawable(context.resources, it) }
    }
}.getOrNull()
