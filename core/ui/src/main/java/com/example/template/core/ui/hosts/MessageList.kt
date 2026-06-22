package com.example.template.core.ui.hosts

import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.checkbox.CheckboxView
import com.example.components.reactions.ReactionsView
import com.example.components.avatar.AvatarColorScheme
import com.example.components.bubbles.BubblesView
import com.example.components.bubbles.LinkBubbleView
import com.example.components.bubbles.MediaBubbleView
import com.example.components.bubbles.VoiceBubbleView
import com.example.components.callmeet.CallMeetView
import com.example.components.systemmessage.SystemMessageView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.template.core.model.AttachmentType
import com.example.template.core.model.CallStatus
import com.example.template.core.model.Message
import com.example.template.core.model.MessageStatus
import com.example.template.core.model.Persona
import com.example.template.core.model.ReplyPreview
import com.example.template.core.model.SystemKind
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalBitmapCache
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.format.TimeFormatter
import com.example.components.bubbles.SelectionMenuItem
import com.example.template.core.ui.appBasic
import com.example.template.core.ui.appSurface02
import com.example.template.core.ui.utils.FragmentHighlightDrawable
import com.example.template.core.ui.utils.attachSelectionHapticWatcher
import com.example.template.core.ui.utils.findMessageTextView
import com.example.template.core.ui.utils.performStrongHaptic

/** Длительность фазы сжатия бабла в press-feedback'е. */
private const val PRESS_SHRINK_MS = 160

/** Hold-фаза: бабл удерживается в peak'е после сжатия, перед spring-возвратом.
 *  Делает фазу сжатия визуально «законченной» прежде чем стартует spring-back и
 *  параллельно с ним появляются чекбоксы — иначе ощущается рассинхрон. */
private const val PRESS_HOLD_MS = 60

/** Задержка вибрации после старта press-feedback'а. Щелчок срабатывает не
 *  одновременно с тапом, а в момент, когда бабл уже видимо проваливается. */
private const val PRESS_HAPTIC_DELAY_MS = 100

/** Доля сжатия pressScale в peak'е (1.0 → 1.0 - PRESS_PEAK_SHRINK). На View
 *  пересчитывается в per-bubble scale, дающий постоянное абсолютное движение
 *  краёв независимо от ширины баббла. См. snapshotFlow на pressScale ниже. */
private const val PRESS_PEAK_SHRINK = 0.07f

/** Целевое абсолютное сжатие каждого края баббла в peak'е (dp). Узкие бабблы
 *  получат больший относительный scale, широкие — меньший, и edge-shift будет
 *  визуально одинаковым. */
private const val PRESS_TARGET_EDGE_SHRINK_DP = 6f

private fun fillWidthLayout() = ViewGroup.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    ViewGroup.LayoutParams.WRAP_CONTENT,
)

/**
 * Подготовка selectable TextView внутри бабла к multi-select взаимодействию.
 *
 * 1) Haptic tick в момент выделения текста (см. SelectionHapticWatcher).
 *    ПРИМЕЧАНИЕ: SelectionHapticWatcher работает через SpanWatcher — он срабатывает когда
 *    CustomSelectionController ставит SELECTION_START/END spans в EditText Spannable.
 *    Если контроллер использует внутренний state без Spannable-спанов — haptic может не
 *    сработать. Оставляем пока: визуально проверить, если broken — запросить у пользователя
 *    добавить `onSelectionStart` callback в CustomSelectionController.
 *
 * 2) Ставит OnTouchListener, который оставляет ТОЛЬКО long-press как способ открыть
 *    text-selection. Editor с setTextIsSelectable(true) иначе ловит double-tap →
 *    что перехватывает повторные тапы по чекбоксу и ломает toggle:
 *      - ACTION_DOWN пропускаем в Editor (он арнирует long-press timer).
 *      - При коротком ACTION_UP (< longPressTimeout) сами вызываем [onTap] и КОНСЬЮМИМ
 *        событие — Editor не видит UP, не считает это «первым тапом» double-tap-пары.
 *      - При длительном ACTION_UP long-press уже сработал в Editor — пропускаем штатно.
 *    Если isClickable=false (после setMessageTextSelectable(false)) — листенер no-op'ит,
 *    событие проваливается до родительского Compose pointerInput.
 *
 * TextClassifier и handle-tint убраны — CustomSelectionController сам рендерит handles
 * и FloatingMenu, не используя TextClassifier / system ActionMode.
 */
private fun setupSelectableBubbleText(tv: TextView, @Suppress("UNUSED_PARAMETER") accentColor: Int, onTap: () -> Unit) {
    // Haptic tick в момент выделения текста (см. SelectionHapticWatcher).
    tv.attachSelectionHapticWatcher()
    tv.setOnTouchListener { v, event ->
        if (!v.isClickable) return@setOnTouchListener false
        when (event.actionMasked) {
            // Consume ACTION_DOWN — иначе View не становится touch target и
            // ACTION_UP не приходит в listener (а AOSP onTouchEvent gating'ом
            // isTextSelectable=false возвращает false). Без consume short-tap
            // не вызывал onTap, и снять чекбокс по tap'у не получалось.
            // Long-press detection идёт в controller через dispatchTouchEvent →
            // handleTouch (до mOnTouchListener) и НЕ зависит от consume здесь.
            MotionEvent.ACTION_DOWN -> true
            MotionEvent.ACTION_UP -> {
                val elapsed = event.eventTime - event.downTime
                val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
                if (elapsed < longPressTimeout) {
                    onTap()
                    true
                } else false
            }
            else -> false
        }
    }
}


/**
 * Вычисляет rect'ы для quote-фрагмента [start, end] в координатах самого [tv]
 * (TV-local) — используется как input для [FragmentHighlightDrawable], который рисуется
 * как background `messageView`'а. Multi-line фрагмент → multiple rect'ы (один per строка).
 * Возвращает emptyList если layout ещё null, range невалиден или TextView не отрисован.
 */
private fun computeFragmentRects(
    tv: android.widget.TextView,
    start: Int,
    end: Int,
): List<android.graphics.Rect> {
    val layout = tv.layout ?: return emptyList()
    val text = tv.text ?: return emptyList()
    val s = start.coerceIn(0, text.length)
    val e = end.coerceIn(s, text.length)
    if (s >= e) return emptyList()

    val lineStart = layout.getLineForOffset(s)
    val lineEnd = layout.getLineForOffset(e)
    // Используем font metrics (ascent/descent от baseline), а НЕ getLineTop/Bottom —
    // последние включают line-spacing (DSTypography.body3M задаёт lineHeight 20sp при
    // textSize 16sp → 4sp лишних над/под глифами). Подсветка по font-metrics совпадает
    // с реальной высотой текста, не залезает на соседние строки.
    val fm = tv.paint.fontMetrics
    val rects = mutableListOf<android.graphics.Rect>()
    for (line in lineStart..lineEnd) {
        val leftPx = if (line == lineStart) layout.getPrimaryHorizontal(s).toInt()
                     else layout.getLineLeft(line).toInt()
        val rightPx = if (line == lineEnd) layout.getPrimaryHorizontal(e).toInt()
                      else layout.getLineRight(line).toInt()
        val baseline = layout.getLineBaseline(line)
        val textTop = (baseline + fm.ascent).toInt()
        val textBottom = (baseline + fm.descent).toInt()
        rects.add(android.graphics.Rect(leftPx, textTop, rightPx, textBottom))
    }
    return rects
}

// Каждый bubble объявляет свой nested SendingState (NONE/SENDING/DELIVERED/READ/ERROR) —
// строго говоря, разные enum'ы с теми же значениями. Поэтому отдельный mapping на тип.
private fun MessageStatus.toBubblesState(): BubblesView.SendingState = when (this) {
    MessageStatus.NONE -> BubblesView.SendingState.NONE
    MessageStatus.SENDING -> BubblesView.SendingState.SENDING
    MessageStatus.DELIVERED -> BubblesView.SendingState.DELIVERED
    MessageStatus.READ -> BubblesView.SendingState.READ
    MessageStatus.ERROR -> BubblesView.SendingState.ERROR
}

private fun MessageStatus.toMediaState(): MediaBubbleView.SendingState = when (this) {
    MessageStatus.NONE -> MediaBubbleView.SendingState.NONE
    MessageStatus.SENDING -> MediaBubbleView.SendingState.SENDING
    MessageStatus.DELIVERED -> MediaBubbleView.SendingState.DELIVERED
    MessageStatus.READ -> MediaBubbleView.SendingState.READ
    MessageStatus.ERROR -> MediaBubbleView.SendingState.ERROR
}

private fun MessageStatus.toVoiceState(): VoiceBubbleView.SendingState = when (this) {
    MessageStatus.NONE -> VoiceBubbleView.SendingState.NONE
    MessageStatus.SENDING -> VoiceBubbleView.SendingState.SENDING
    MessageStatus.DELIVERED -> VoiceBubbleView.SendingState.DELIVERED
    MessageStatus.READ -> VoiceBubbleView.SendingState.READ
    MessageStatus.ERROR -> VoiceBubbleView.SendingState.ERROR
}

private fun MessageStatus.toLinkState(): LinkBubbleView.SendingState = when (this) {
    MessageStatus.NONE -> LinkBubbleView.SendingState.NONE
    MessageStatus.SENDING -> LinkBubbleView.SendingState.SENDING
    MessageStatus.DELIVERED -> LinkBubbleView.SendingState.DELIVERED
    MessageStatus.READ -> LinkBubbleView.SendingState.READ
    MessageStatus.ERROR -> LinkBubbleView.SendingState.ERROR
}

/**
 * Текст для replyText-поля баббла:
 *   • если есть snapshot и оригинал ещё «живой» в чате → snapshot.text;
 *   • если оригинал заменён на Message.System(MessageDeleted) или вовсе пропал
 *     → «Удалил(а) сообщение» (имя автора уйдёт в replySender, грамматика читается:
 *     «{Имя}\nУдалил(а) сообщение»);
 *   • если snapshot'а нет → null (компонент скрывает reply-блок).
 */
private fun resolveReplyText(msg: Message, byId: Map<String, Message>): String? {
    val rt = msg.replyTo ?: return null
    val original = byId[rt.originalId]
    val isDeleted = original == null ||
        (original is Message.System && original.kind == SystemKind.MessageDeleted)
    return if (isDeleted) "Удалил(а) сообщение" else rt.text
}

// Centered pill для всех system-row'ов (date-разделители + Joined и проч.). Обёртка
// над `SystemMessageView` из `:components` — компонент сам рулит фоном/радиусом/типографикой,
// цвета берутся из `brand.systemMessageColorScheme(isDark)`. Никаких внутренних vertical
// padding — top-зазор контролируется caller'ом из `computeTopPadding`.
//
// [isHighlighted] — при тапе на reply-block на удалённое сообщение пульсируем фон
// на всю ширину row'а basic10-цветом (alpha 0→1→0 за 500+500мс), визуальный эквивалент
// bubble-pulse'а. Pulse ограничен высотой pill'а (не дотягивается до topPad-зазора над ним).
@Composable
private fun SystemMessageRow(
    text: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val colorScheme = remember(brand, isDark) { brand.systemMessageColorScheme(isDark) }
    val pulseProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            pulseProgress.snapTo(0f)
            pulseProgress.animateTo(1f, tween(500))
            pulseProgress.animateTo(0f, tween(500))
        } else {
            pulseProgress.snapTo(0f)
        }
    }
    // Внешний Box несёт padding-top (topPad из computeTopPadding). Pulse живёт ВНУТРИ
    // inner Box'а (fillMaxWidth × pill height) — иначе matchParentSize накрыл бы и
    // padding-зазор сверху.
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (pulseProgress.value > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(appBasic(isDark, 0.10f * pulseProgress.value)),
                )
            }
            AndroidView(
                factory = { ctx ->
                    SystemMessageView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    }
                },
                update = { view -> view.configure(text = text, colorScheme = colorScheme) },
            )
        }
    }
}

private data class OverlayState(val label: String?, val dateRowVisible: Boolean)

// Sticky-date chip: появляется во время скролла когда inline DateRow текущей секции
// вне viewport; через 1.4s после остановки скролла плавно исчезает. Также прячется
// мгновенно если DateRow попала в viewport (чтобы не дублировать). Enter/exit — fadeIn/Out 150ms.
@Composable
private fun StickyDateOverlay(
    modifier: Modifier,
    listState: LazyListState,
    dateForRow: Map<String, String>,
    dateRowKeys: Set<String>,
) {
    val overlayState by remember(dateForRow, dateRowKeys) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            // reverseLayout=true → last() = topmost visible item
            val topKey = visible.lastOrNull()?.key as? String
            val date = topKey?.let { dateForRow[it] }
            if (date.isNullOrEmpty()) return@derivedStateOf OverlayState(null, false)
            val rowVis = visible.any { info ->
                val k = info.key as? String ?: return@any false
                k in dateRowKeys && dateForRow[k] == date
            }
            OverlayState(date, rowVis)
        }
    }

    val hasLabel = !overlayState.label.isNullOrEmpty() && !overlayState.dateRowVisible
    val scrolling = listState.isScrollInProgress
    var showSticky by remember { mutableStateOf(false) }
    // hasLabel=false → мгновенный скрыть (DateRow стала видимой / нет даты).
    // scrolling=true → показать. scrolling=false и hasLabel — продержать 1.4s и спрятать.
    LaunchedEffect(scrolling, hasLabel) {
        when {
            !hasLabel -> showSticky = false
            scrolling -> showSticky = true
            else -> { delay(1400); showSticky = false }
        }
    }

    AnimatedVisibility(
        visible = showSticky,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(150)),
        modifier = modifier,
    ) {
        SystemMessageRow(text = overlayState.label ?: "")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageList(
    messages: List<Message>,
    modifier: Modifier = Modifier,
    personaByUserId: (String) -> Persona? = { null },
    // В P2P-чате собеседник один и известен из заголовка чата — имя отправителя в
    // SOMEONE-баблах избыточно. В Group/Channel чатах имя нужно. Когда isP2P=true,
    // мы передаём пустой `sender` в bubble.configure(...); компоненты уже умеют
    // гейтить пустой sender (см. setupSomeoneBubble в каждом из них).
    isP2P: Boolean = false,
    onBubbleTap: (messageId: String) -> Unit = {},
    onBubbleLongPress: (id: String) -> Unit = {},
    onQuoteSelected: (id: String, start: Int, end: Int) -> Unit = { _, _, _ -> },
    onReactionTap: (messageId: String, emoji: String) -> Unit = { _, _ -> },
    onAddReactionTap: (messageId: String) -> Unit = {},
    onSwipeReply: (messageId: String) -> Unit = {},
    onReplyBlockTap: (replyTo: ReplyPreview) -> Unit = {},
    listState: LazyListState? = null,
    selectionActive: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: (messageId: String) -> Unit = {},
    highlightedMessageId: String? = null,
    /** Если highlightedMessageId совпадает с msg.id и это Text с quote-reply — после bubble-pulse
     *  запускается анимация подсветки [start, end] фрагмента в тексте оригинала. */
    highlightedQuoteRange: Pair<Int, Int>? = null,
    /** StateFlow активного voice-плеера. Ссылка стабильна на жизнь VM — MessageList не
     *  recompose'ится на тиках playback'а. `collectAsState()` вызывается ВНУТРИ items()
     *  voice-bubble item scope — обновления изолированы в одном баббле, не каскадятся
     *  на всё дерево.
     *  Дефолт — пустой MutableStateFlow(null), для consumer'ов без voice-функционала. */
    voicePlaybackFlow: StateFlow<VoicePlayback?> = MutableStateFlow(null),
    onVoicePlayPauseClick: (messageId: String) -> Unit = {},
    /** Drag по waveform: компонент стреляет на ACTION_UP с position 0..1; ожидается обновление
     *  voicePlayback.position в VM, чтобы баббл рекомпозился с новым playbackPosition. */
    onVoiceSeek: (messageId: String, position: Float) -> Unit = { _, _ -> },
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val bitmapCache = LocalBitmapCache.current
    // Подписка на фоновый прогрев аватарок: при асинхронной загрузке PNG'шек
    // SOMEONE-bubble без этого так и остался бы без аватара до случайной рекомпозиции.
    val cacheVersion = bitmapCache.version.value

    // ОДИН shared Animatable для selection-перехода. value=0 ↔ checkbox hidden,
    // бабл на 0; value=1 ↔ checkbox visible, бабл +32dp (SOMEONE).
    val selectionAnim = remember {
        androidx.compose.animation.core.Animatable(if (selectionActive) 1f else 0f)
    }

    // ID баббла, для которого только что сработал long-press → старт multi-select.
    // Триггерит per-bubble spring-ping (scale 1→0.93→1 на View.getChildAt(1)).
    // Сбрасывается в null после завершения анимации. См. pressScale ниже в items().
    val pressedFeedbackId = remember { mutableStateOf<String?>(null) }
    // shouldMountCheckbox управляется ВНУТРИ LaunchedEffect'а, а не через `isRunning`.
    // Раньше: `shouldMount = selectionActive || isRunning`. Проблема: при toggle
    // `selectionActive: true→false` Compose делает recompose синхронно ДО запуска
    // корутины LaunchedEffect'а, isRunning ещё false → shouldMount=false → checkbox
    // unmount → потом корутина стартует, isRunning=true → mount + slide-out. Рывок.
    //
    // Сейчас: на enter mount'им immediately и анимируем 0→1; на exit анимируем 1→0
    // СНАЧАЛА, mount=false ставим только ПОСЛЕ завершения animation. Recompose из-за
    // shouldMount меняется максимум 2 раза за переход (enter start / exit end).
    var shouldMountCheckbox by remember { mutableStateOf(selectionActive) }
    LaunchedEffect(selectionActive) {
        val spec = androidx.compose.animation.core.spring<Float>(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
        )
        if (selectionActive) {
            shouldMountCheckbox = true
            // Чекбоксы стартуют параллельно со spring-возвратом баббла, то есть
            // ПОСЛЕ того как фаза сжатия + hold полностью отработали. Пользователь
            // видит «бабл провалился → задержался в peak'е → одновременно
            // поплыли вверх и проявились чекбоксы». Без hold'а ощущается рассинхрон.
            // Условный на pressedFeedbackId: если в selection вошли через context-menu
            // «Выбрать» (без press-feedback) — задержки нет, чекбоксы появляются сразу.
            if (pressedFeedbackId.value != null) {
                kotlinx.coroutines.delay((PRESS_SHRINK_MS + PRESS_HOLD_MS).toLong())
            }
            selectionAnim.animateTo(1f, animationSpec = spec)
        } else {
            selectionAnim.animateTo(0f, animationSpec = spec)
            shouldMountCheckbox = false
        }
    }
    val bubblesScheme = brand.bubblesColorScheme(isDark)
    val mediaScheme = brand.mediaColorScheme(isDark)
    val voiceScheme = brand.voiceBubbleColorScheme(isDark)
    val linkScheme = brand.linkBubbleColorScheme(isDark)
    val callMeetScheme = brand.callMeetColorScheme(isDark)

    val checkboxScheme = remember(brand, isDark) { brand.checkboxColorScheme(isDark) }

    // Avatar-схемы по gradientIndex: бренд даёт N пар (top/bottom) для INITIALS-градиента
    // в AvatarView. Persona.gradientIndex выбирает свою пару, чтобы у разных персон были
    // разные цветные кружки. Мирорит логику ChatListHost.
    val avatarSchemesByIndex: Map<Int, AvatarColorScheme> = remember(brand, isDark) {
        val pairs = brand.avatarGradientPairs(isDark)
        val base = brand.avatarColorScheme(isDark)
        (pairs.indices).associateWith { i ->
            base.copy(
                initialsGradientTop = pairs[i].top,
                initialsGradientBottom = pairs[i].bottom,
            )
        }
    }

    // Chat-style лента: latest сообщение прилипает к низу, листать вверх → старые.
    // Данные приходят в хронологическом порядке (oldest→latest). flattenToRows вставляет
    // date-separator и system rows; результат переворачивается через asReversed().
    // reverseLayout=true ставит индекс 0 в нижнюю точку viewport'а.
    val now = remember(messages) { System.currentTimeMillis() }
    val flattened = remember(messages, now, personaByUserId) {
        flattenToRows(messages, now, personaByUserId) { ts ->
            TimeFormatter.formatDateSeparator(ts, now)
        }
    }
    val rows = flattened.rows
    val dateForRow = flattened.dateForRow
    val dateRowKeys = flattened.dateRowKeys
    val reversed = remember(rows) { rows.asReversed() }
    val groupPositions = remember(rows) { computeGroupPositions(rows) }
    val topPaddingPerRow = remember(rows, groupPositions) { computeTopPadding(rows, groupPositions) }

    // ID-индекс для проверки «оригинал ещё жив?» в reply-фолбэке. Пересчитывается
    // когда меняется messages — это дешёвая операция (associateBy один проход).
    val messagesById = remember(messages) { messages.associateBy { it.id } }

    // Активный TextView с открытым FloatingMenu (CustomSelectionController).
    // Ранее обновлялся через QuoteActionModeCallback.onCreated/onDestroyed.
    // После миграции на CustomSelectionController больше не заполняется — контроллер
    // сам скрывает FloatingMenu при тапе вне. State оставлен как no-op guard
    // (handleRowTap читает .value, всегда null → обычный onBubbleTap).
    val activeSelectionTv = remember { mutableStateOf<android.widget.TextView?>(null) }

    // Активный bubble с text-selection через CustomSelectionController. Ставится из
    // bubble.onMessageTextSelectionStart (long-press), снимается из onMessageTextSelectionEnd
    // (программный clearMessageTextSelection / disable). handleRowTap в любом row checks
    // — если есть active selection, tap чистит её (вместо toggle checkbox).
    val activeSelectionOwner = remember { mutableStateOf<android.view.View?>(null) }
    val activeSelectionClearer = remember { mutableStateOf<(() -> Unit)?>(null) }

    val internalListState = rememberLazyListState()
    // Если вызывающий передал внешний listState (для scroll-to-original из ChatDetailScreen) —
    // используем его; иначе — внутренний. Это позволяет ChatDetailScreen контролировать скролл
    // из onReplyBlockTap-лямбды.
    val effectiveListState = listState ?: internalListState
    // Авто-скролл к самому свежему сообщению при появлении нового id. Ключ — именно id
    // последнего сообщения (а не messages.size или ссылка на список), чтобы status-апдейт
    // DELIVERED→READ существующего bubble (тот же id, меняется только поле) НЕ дёргал scroll.
    // reverseLayout=true → индекс 0 это «низ»; на холодном старте мы и так уже на 0, поэтому
    // первый запуск эффекта получается no-op.
    val latestId = messages.lastOrNull()?.id
    LaunchedEffect(latestId) {
        if (latestId != null) effectiveListState.animateScrollToItem(0)
    }

    // V1 selection clip rects (см. также Fullscreen picker в QuoteBubblePreview):
    //  • handleRect — bounds chat-list'а + 24dp снизу (handle anchor на baseline, body
    //    свисает вниз — без overflow'а маркер исчезал бы раньше когда выделена нижняя строка);
    //  • menuRect — строго bounds, чтобы floating menu при scroll'е селекции за пределы
    //    viewport'а пинилась к ближайшему краю с 8dp safe-зоной, а не за пределы viewport'а.
    // Compose LazyColumn — НЕ native scroll container, SelectionClipRect не находит его и
    // fallback'ится на screen → handles/menu могут вылезать в область MessagePanel'а / header'а.
    val v1HandleClipRect = remember { mutableStateOf<android.graphics.Rect?>(null) }
    val v1MenuClipRect = remember { mutableStateOf<android.graphics.Rect?>(null) }
    val v1HandleOverflowPx = with(LocalDensity.current) { 24.dp.roundToPx() }
    val v1ScrollInProgress = effectiveListState.isScrollInProgress
    // Auto-scroll callback для V1: когда юзер тащит маркер к краю chat-list'а, controller
    // репортит dy → LazyListState.dispatchRawDelta крутит LazyColumn. Без этого native-fallback
    // (View.scrollBy) не находит scroll-контейнер (LazyColumn — не View) и auto-scroll не работает.
    // ВАЖНО: LazyColumn здесь с reverseLayout=true (сообщения снизу), поэтому знак dy
    // инвертирован относительно V3 — иначе drag к нижнему краю прокручивает к старым
    // сообщениям, а пользователь ожидает раскрытие новых.
    val v1AutoScrollCallback: (Int) -> Unit = remember(effectiveListState) {
        { dy -> effectiveListState.dispatchRawDelta(-dy.toFloat()) }
    }
    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val sz = coords.size
                val left = pos.x.toInt()
                val top = pos.y.toInt()
                val right = left + sz.width
                val bottom = top + sz.height
                val menuR = android.graphics.Rect(left, top, right, bottom)
                val handleR = android.graphics.Rect(left, top, right, bottom + v1HandleOverflowPx)
                if (v1MenuClipRect.value != menuR) v1MenuClipRect.value = menuR
                if (v1HandleClipRect.value != handleR) v1HandleClipRect.value = handleR
            },
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = effectiveListState,
        reverseLayout = true,
        // С reverseLayout=true contentPadding.bottom добавляется к НИЖНЕМУ краю viewport'а
        // (там, где anchor у индекса 0). Это и есть зазор между последним сообщением и
        // MessagePanel'ом сверху неё.
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(reversed, key = { it.key }) { row ->
            val topPad = topPaddingPerRow[row.key] ?: 0.dp
            when (row) {
                is RowItem.SystemRow -> SystemMessageRow(
                    text = row.text,
                    modifier = Modifier.padding(top = topPad),
                    isHighlighted = row.messageId != null && row.messageId == highlightedMessageId,
                )
                is RowItem.Bubble -> {
                    val msg = row.message
                    val position = groupPositions[msg.id] ?: RowGroupPosition.SINGLE
                    // Зазор НАД item'ом управляется через topPad из computeTopPadding.
                    // С reverseLayout=true «выше визуально» = «раньше по времени», что совпадает
                    // с натуральной семантикой групп.
                    val isFirstInGroup = position == RowGroupPosition.FIRST || position == RowGroupPosition.SINGLE
                    val isLastInGroup = position == RowGroupPosition.LAST || position == RowGroupPosition.SINGLE
                    @Suppress("UNUSED_VARIABLE") val cacheVersionRead = cacheVersion
                    // Persona / avatar / brand-scheme для SOMEONE — общий resolve для всех типов
                    // баблов. Для MY-сообщений senderName/senderAvatar просто не показываются
                    // компонентом, так что вычисление безвредно (и persona-lookup быстрый).
                    val persona: Persona? = if (!msg.isMine) personaByUserId(msg.senderId) else null
                    // В P2P баббл-имя отправителя скрываем — собеседник один и известен из header'а.
                    // Avatar и senderInitials оставляем (компонент рисует их независимо).
                    val senderName = if (isP2P) "" else persona?.let { "${it.firstName} ${it.lastName}" }.orEmpty()
                    val senderInitials = persona?.initials
                    val senderAvatar: Bitmap? = bitmapCache.get(persona?.avatarAsset)
                    val avatarSchemeIdx = (persona?.gradientIndex ?: 0)
                        .coerceAtLeast(0)
                        .let { it % avatarSchemesByIndex.size.coerceAtLeast(1) }
                    val avatarScheme = avatarSchemesByIndex[avatarSchemeIdx]
                        ?: avatarSchemesByIndex.values.firstOrNull()
                        ?: AvatarColorScheme.DEFAULT
                    // NBSP ( ) между First/Last name: BubblesView.replySenderView без
                    // maxLines — Android line-breaker иначе переносит фамилию на новую строку
                    // при узком баббле. NBSP склеивает имя/фамилию в одно "слово" для wrap'а.
                    val replySender = msg.replyTo?.authorName?.replace(' ', ' ')
                    val replyText = resolveReplyText(msg, messagesById)
                    // Замеряем визуальный правый край баббла (bubbleContent.right внутри BubblesView/
                    // MediaBubbleView) через OnLayoutChangeListener в factory ниже. Используется
                    // ReplyIndicator'ом для позиционирования по центру (bubbleRight, screenRight).
                    val bubbleRightPxState = remember(msg.id) { mutableStateOf(0) }
                    val rowWidthPxState = remember(msg.id) { mutableStateOf(0) }
                    // Y-смещение индикатора от центра bubble-row Box'а. По умолчанию 0 (= центр баббла).
                    // Когда баббл частично за viewport'ом, обновляется в onGloballyPositioned ниже, чтобы
                    // индикатор центрировался на ВИДИМОЙ части, а не на полной высоте row'а (иначе уезжает за экран вместе с баблом).
                    val rowYAdjustmentState = remember(msg.id) { mutableStateOf(0f) }
                    // animatedShift'ы выведены наружу (selectionAnim.value в MessageList scope) —
                    // ниже translationX-формулы вычисляются ВНУТРИ graphicsLayer-лямбды
                    // (deferred reads, draw phase, без layout invalidate).
                    // SwipeToReplyItem теперь имеет одинаковую composable-структуру для
                    // enabled=true/false (re-inflate бабла нет), поэтому можно гейтить
                    // через selectionActive без визуальных артефактов.
                    SwipeToReplyItem(
                        messageId = msg.id,
                        enabled = (msg is Message.Text || msg is Message.Media || msg is Message.Voice) && !selectionActive,
                        onTriggerReply = onSwipeReply,
                        modifier = Modifier.fillMaxWidth().padding(top = topPad),
                    ) {
                    // rememberUpdatedState: pointerInput block стабилен по ключу msg.id —
                    // перезапускается только при смене id. Чтобы при этом не захватить
                    // stale-closure на лямбды onBubbleTap/onBubbleLongPress, обращаемся
                    // к ним через State-обёртки (tapLatest/longPressLatest) — они всегда
                    // дают актуальную лямбду даже после внешней рекомпозиции.
                    val tapLatest = rememberUpdatedState(onBubbleTap)
                    val longPressContext = LocalContext.current
                    val longPressLatest = rememberUpdatedState<(String) -> Unit> { id ->
                        // Press-feedback только на ВХОД в multi-select (long-press вне
                        // selection-mode'а). Long-press внутри уже активного multi-select
                        // — пользователь добавляет ещё один баббл к выделению — отклика
                        // НЕ даём (это не «вход», а продолжение работы). Tap-toggle в
                        // multi-select анимируется отдельно — см. handleRowTap ниже.
                        // Вибрация запускается из pressScale LaunchedEffect'а с задержкой
                        // PRESS_HAPTIC_DELAY_MS — щелчок попадает в момент видимого
                        // проваливания баббла, не в тот же кадр что user touch.
                        if (!selectionActive) pressedFeedbackId.value = id
                        onBubbleLongPress(id)
                    }
                    // Унифицированная логика тапа по ЛЮБОЙ точке row'а — Compose pointerInput,
                    // TextView (см. setupSelectableBubbleText), reply-block (см. onReplyBlockClick).
                    // Приоритеты:
                    //   1. Если активен CustomSelectionController на любом бабле (`activeSelectionClearer`) —
                    //      тап ВЕЗДЕ чистит text-selection и НЕ trogает checkbox.
                    //   2. Legacy: native text-selection ActionMode (activeSelectionTv != null) — clear text.
                    //   3. Иначе — обычный onBubbleTap (rule 1: toggle multi-select).
                    val handleRowTap: () -> Unit = {
                        val clearer = activeSelectionClearer.value
                        val activeTv = activeSelectionTv.value
                        when {
                            clearer != null -> clearer()
                            activeTv != null -> {
                                val sp = activeTv.text as? android.text.Spannable
                                if (sp != null) android.text.Selection.removeSelection(sp)
                                activeTv.clearFocus()
                                activeSelectionTv.value = null
                            }
                            else -> {
                                // Press-feedback не нужен на тапах внутри multi-select —
                                // ни на toggle чекбокса, ни на выход (тап по последнему
                                // выбранному). Анимация бабла используется ИСКЛЮЧИТЕЛЬНО
                                // для подсветки входа в multi-select через long-press.
                                tapLatest.value(msg.id)
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(msg.id) {
                                detectTapGestures(
                                    onTap = { handleRowTap() },
                                    onLongPress = { longPressLatest.value(msg.id) },
                                )
                            },
                    ) {
                        // Custom Layout: height row'а определяется ИСКЛЮЧИТЕЛЬНО bubble row Box'ом
                        // (первый measurable). Checkbox (второй measurable, опциональный) измеряется
                        // independently, но в parent height НЕ contribits — он лишь place'ится
                        // vertically centered внутри bubble's height. Поэтому когда selection
                        // включается/выключается, row height не меняется, даже если bubble короче
                        // чем CheckboxView (24dp). Box-overlay (`max(children.height)`) не подходил.
                        //
                        // Bubble всегда fillMaxWidth, его внутренний leading-margin (для SOMEONE с
                        // аватаркой) служит slot'ом под чекбокс. Checkbox layout-wise existence
                        // (mount/unmount) controlled через shouldMountCheckbox — гарантирует
                        // отсутствие лишних AndroidView в дереве вне selection.
                        Layout(
                            modifier = Modifier.fillMaxWidth(),
                            content = {
                        // Bubble row: ReplyIndicator (под баблом по z-order) + translated-bubble Box.
                        // ReplyIndicator идёт ПЕРВЫМ child'ом — рендерится под баблом. Когда баббл
                        // широкий и накрывает позицию индикатора (короткий gap для MY-баблов),
                        // индикатор уходит под баббл.
                        // onGloballyPositioned считает Y-смещение индикатора: для длинного баббла,
                        // частично уехавшего за viewport, индикатор центрируется на ВИДИМОЙ части,
                        // а не на полной высоте row'а (иначе уезжает за экран вместе с баблом).
                        // boundsInWindow клипается LazyColumn'ом, positionInWindow + size дают полные
                        // границы — разница их центров и есть искомый сдвиг.
                        val containerHeightPx = with(LocalDensity.current) { iconContainerSize.toPx() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    // Пересчитываем rowYAdjustmentState на каждый scroll/layout pass. Использует
                                    // ReplyIndicator (вне selection) и Checkbox (в selection-mode) — оба должны
                                    // центрироваться по визибл-части баббла для длинных сообщений, частично
                                    // ушедших за viewport. НЕ гейтим по dragOffsetPx/selectionActive: graphicsLayer
                                    // не триггерит onGloballyPositioned, а пред-вычисленное значение нужно сразу
                                    // на первом кадре свайпа/при входе в selection.
                                    val pos = coords.positionInWindow()
                                    val sizePx = coords.size
                                    val visible = coords.boundsInWindow()
                                    val fullCenterY = pos.y + sizePx.height / 2f
                                    val visibleHeight = visible.height
                                    val targetCenterY = when {
                                        visibleHeight >= containerHeightPx -> {
                                            (visible.top + visible.bottom) / 2f
                                        }
                                        pos.y < visible.top -> {
                                            // Clipped сверху — видимая часть в нижней половине баббла.
                                            // Якорим индикатор к visible.bottom, чтобы остался в кадре.
                                            visible.bottom - containerHeightPx / 2f
                                        }
                                        else -> {
                                            // Clipped снизу — видимая часть в верхней половине.
                                            visible.top + containerHeightPx / 2f
                                        }
                                    }
                                    rowYAdjustmentState.value = targetCenterY - fullCenterY
                                },
                        ) {
                        // Позиционирование: горизонтально на центре расстояния (правый край баббла →
                        // правый край row'а), вертикально — по центру баббл-row Box'а. bubbleVisualRight
                        // = bubbleContent.right (изм-ный через OnLayoutChangeListener) + dragOffsetPx
                        // (текущий eased-сдвиг баббла) — даёт мгновенный визуальный край с учётом свайпа.
                        ReplyIndicator(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .absoluteOffset {
                                    val bubbleVisualRight = bubbleRightPxState.value + dragOffsetPx
                                    val midpoint = (bubbleVisualRight + rowWidthPxState.value) / 2f
                                    val iconRadiusPx = iconContainerSize.toPx() / 2
                                    IntOffset(
                                        x = (midpoint - iconRadiusPx).toInt(),
                                        y = rowYAdjustmentState.value.toInt(),
                                    )
                                },
                        )
                        // Двухстадийная подсветка по тапу на reply-block (фазы перекрываются):
                        //  Stage A (bubble pulse, 0..1000ms): basic10 фон, alpha 0→1→0 за 500in+500out.
                        //  Stage B (fragment overlay, 400..1800ms): launch'ится через 400ms ПАРАЛЛЕЛЬНО,
                        //    in 200ms (scale 2.0→1.0, alpha 0→1), hold 1000ms, out 200ms (alpha 1→0).
                        // fragProgress: 0..1 = in, 1..2 = hold, 2..3 = out, иначе = idle/done.
                        val isHighlighted = msg.id == highlightedMessageId
                        val hasQuoteHighlight = isHighlighted && highlightedQuoteRange != null && (
                            msg is Message.Text || (msg is Message.Media && !msg.caption.isNullOrEmpty())
                        )
                        val pulseProgress = remember(msg.id) { androidx.compose.animation.core.Animatable(0f) }
                        val fragProgress = remember(msg.id) { androidx.compose.animation.core.Animatable(0f) }
                        LaunchedEffect(isHighlighted, hasQuoteHighlight) {
                            if (isHighlighted) {
                                pulseProgress.snapTo(0f)
                                fragProgress.snapTo(0f)
                                // Fragment стартует через 400ms (параллельно с tail bubble-pulse'а).
                                // in 600ms FastOutSlowIn, hold 1800ms, out 600ms LinearOutSlowIn.
                                val fragJob = if (hasQuoteHighlight) launch {
                                    kotlinx.coroutines.delay(400)
                                    fragProgress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
                                    fragProgress.animateTo(2f, tween(1800))
                                    fragProgress.animateTo(3f, tween(600, easing = LinearOutSlowInEasing))
                                } else null
                                pulseProgress.animateTo(1f, tween(500))
                                pulseProgress.animateTo(0f, tween(500))
                                fragJob?.join()
                            } else {
                                pulseProgress.snapTo(0f)
                                fragProgress.snapTo(0f)
                            }
                        }
                        val pulseAlpha = pulseProgress.value
                        // Fragment rect computation: TextView coords относительно BubblesView,
                        // считается через ссылку на view + view-walk до messageView TextView.
                        // View, а не BubblesView — Media bubble использует MediaBubbleView, а у обоих
                        // подсветка должна работать (msg.body для Text, msg.caption для Media).
                        val bubbleViewRef = remember(msg.id) { mutableStateOf<View?>(null) }
                        // Press-feedback: spring-ping scale на child[1] (bubbleContent —
                        // только бабл-графика, child[0] это bubbleAvatar для SOMEONE).
                        // View.scaleX/Y не affect'ит layout → соседние сообщения, аватарка
                        // и реакции (рендерятся отдельным AndroidView ниже) не двигаются.
                        // bubbleViewRef должен быть set'нут в factory (BubblesView/MediaBubbleView/
                        // VoiceBubbleView/LinkBubbleView/CallMeetView). У CallMeetView структура
                        // другая (FrameLayout → row → avatar+bubble) — collector ниже её учитывает
                        // и скейлит внутренний bubble под row.
                        val pressScale = remember(msg.id) {
                            androidx.compose.animation.core.Animatable(1f)
                        }
                        LaunchedEffect(pressedFeedbackId.value, msg.id) {
                            if (pressedFeedbackId.value == msg.id) {
                                // Шелчок-вибрация в момент видимого проваливания —
                                // launch'ится параллельно, не блокирует animateTo.
                                launch {
                                    kotlinx.coroutines.delay(PRESS_HAPTIC_DELAY_MS.toLong())
                                    performStrongHaptic(longPressContext)
                                }
                                // Shrink — tween с предсказуемой длительностью, чтобы
                                // selectionAnim ниже мог точно дождаться MIN'а перед
                                // показом чекбоксов. Spring-back на возврате — для
                                // пружинистого ощущения.
                                pressScale.snapTo(1f)
                                pressScale.animateTo(
                                    1f - PRESS_PEAK_SHRINK,
                                    // Ease-in / accelerate: бабл «проваливается» — медленный
                                    // старт, резкое ускорение к peak'у. (0.5/0/0.85/0.3) даёт
                                    // выраженный fall-character.
                                    animationSpec = tween(
                                        PRESS_SHRINK_MS,
                                        easing = androidx.compose.animation.core.CubicBezierEasing(0.5f, 0f, 0.85f, 0.3f),
                                    ),
                                )
                                // Hold в peak'е — фаза сжатия читается как «завершённая»
                                // прежде чем spring-back и появление чекбоксов стартуют
                                // параллельно. Без hold'а пользователь видел рассинхрон.
                                kotlinx.coroutines.delay(PRESS_HOLD_MS.toLong())
                                pressScale.animateTo(
                                    1f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                                    ),
                                )
                                if (pressedFeedbackId.value == msg.id) pressedFeedbackId.value = null
                            }
                        }
                        LaunchedEffect(bubbleViewRef.value) {
                            val view = bubbleViewRef.value as? ViewGroup ?: return@LaunchedEffect
                            val density = view.resources.displayMetrics.density
                            val targetEdgePx = PRESS_TARGET_EDGE_SHRINK_DP * density
                            snapshotFlow { pressScale.value }.collect { v ->
                                // CallMeetView (FrameLayout → row → avatar+bubble): целевой child —
                                // bubble под row, чтобы аватар (если есть) не сжимался — как у
                                // BubblesView, где аватар отдельным child'ом вне bubbleContent.
                                val child = when (view) {
                                    is CallMeetView -> (view.getChildAt(0) as? ViewGroup)?.getChildAt(1)
                                    else -> if (view.childCount >= 2) view.getChildAt(1) else null
                                } ?: return@collect
                                if (child.width > 0) child.pivotX = child.width / 2f
                                if (child.height > 0) child.pivotY = child.height / 2f
                                // pressScale.value линейно пробегает [1.0, 1.0-PEAK_SHRINK, 1.0]
                                // (+overshoot на spring-возврате). Перекладываем в per-bubble
                                // scale так, чтобы absolute edge-shift = targetEdgePx
                                // независимо от ширины баббла. Clamp защищает крайние случаи:
                                // нижний (0.02) — чтобы очень широкие бабблы не теряли
                                // отклик; верхний (0.12) — чтобы крошечные бабблы не сжимались
                                // до неузнаваемого.
                                val widthPx = kotlin.math.max(child.width, 1).toFloat()
                                val maxDelta = (2f * targetEdgePx / widthPx).coerceIn(0.02f, 0.12f)
                                val animProgress = (1f - v) / PRESS_PEAK_SHRINK
                                val effectiveScale = 1f - animProgress * maxDelta
                                child.scaleX = effectiveScale
                                child.scaleY = effectiveScale
                            }
                        }
                        var fragmentRects by remember(msg.id) { mutableStateOf<List<android.graphics.Rect>>(emptyList()) }
                        if (hasQuoteHighlight) {
                            val msgText = when (msg) {
                                is Message.Text -> msg.body
                                is Message.Media -> msg.caption.orEmpty()
                                else -> ""
                            }
                            val range = highlightedQuoteRange
                            // Compose-scope значения, которые нужны coroutine'у внутри LaunchedEffect'а
                            // (LocalDensity/brand читаются только в Compose-scope, не в snapshotFlow).
                            val highlightColor = brand.accentColor(isDark)
                            val highlightDensity = LocalDensity.current
                            val highlightCornerPx = with(highlightDensity) { 4.dp.toPx() }
                            val highlightExpandPx = with(highlightDensity) { 2.dp.toPx() }.toInt()
                            // bubbleViewRef.value в ключах — чтобы эффект перезапустился, когда
                            // баббл скомпозится mid-flight первой анимации (ChatDetailScreen
                            // запускает animateScrollToItem ПАРАЛЛЕЛЬНО с requestHighlight).
                            // Тогда animateScrollBy ниже прервёт первый скролл и продолжит
                            // к фрагменту одним движением — пользователь видит цельный путь.
                            LaunchedEffect(msg.id, range, bubbleViewRef.value) {
                                if (range == null || msgText.isEmpty()) { fragmentRects = emptyList(); return@LaunchedEffect }
                                val v = bubbleViewRef.value ?: return@LaunchedEffect
                                // Ждём кадр чтобы tv.layout был не null (после measure pass'а Bubble).
                                kotlinx.coroutines.delay(16)
                                val tv = v.findMessageTextView(msgText) ?: return@LaunchedEffect
                                val rects = computeFragmentRects(tv, range.first, range.second)
                                fragmentRects = rects
                                if (rects.isEmpty()) return@LaunchedEffect
                                // Вторичный скролл: если фрагмент вне видимой области
                                // (сообщение длиннее экрана), досматриваем до него.
                                // С reverseLayout=true animateScrollToItem прижимает конец
                                // сообщения к низу экрана — цитата в начале уходит выше viewport.
                                // Ищем item по key == msg.id (не по reversedIdx: он не учитывает
                                // date-разделители и system rows в LazyColumn).
                                val layout = effectiveListState.layoutInfo
                                val itemInfo = layout.visibleItemsInfo
                                    .firstOrNull { it.key == msg.id }
                                if (itemInfo != null) {
                                    val vpStart = layout.viewportStartOffset
                                    val vpEnd = layout.viewportEndOffset
                                    // reverseLayout=true: item.offset = расстояние от нижнего
                                    // края item'а до нижней границы viewport'а.
                                    // item_top = vpEnd - offset - size.
                                    val itemTop = vpEnd - itemInfo.offset - itemInfo.size
                                    // rects теперь TV-local — добавляем вертикальный offset
                                    // TV-внутри-bubble (reply-section / sender-name / padding).
                                    val rowLoc = IntArray(2); v.getLocationInWindow(rowLoc)
                                    val tvLoc = IntArray(2); tv.getLocationInWindow(tvLoc)
                                    val fragTop = itemTop + (tvLoc[1] - rowLoc[1]) + rects.first().top
                                    // Целевая позиция: начало цитаты на 25% от верха viewport'а.
                                    // reverseLayout=true: animateScrollBy(+) двигает контент ВНИЗ
                                    // (fragTop растёт), (-) — ВВЕРХ. Знак scrollDelta корректен
                                    // для обоих случаев (фрагмент выше или ниже цели).
                                    val targetTop = vpStart + (vpEnd - vpStart) * 0.25f
                                    val scrollDelta = targetTop - fragTop
                                    if (kotlin.math.abs(scrollDelta) > 1f) {
                                        effectiveListState.animateScrollBy(scrollDelta)
                                    }
                                }
                                // Drawable как TextView.background → z-order: bubble fill →
                                // подсветка → glyphs (SelectableEditText в init задаёт
                                // background=null, слот свободен). snapshotFlow драйвит
                                // alpha+scale из fragProgress.value; finally возвращает
                                // background в null при cancel/exit coroutine'а.
                                val drawable = FragmentHighlightDrawable(
                                    color = highlightColor,
                                    cornerPx = highlightCornerPx,
                                    stepCornerPx = highlightCornerPx / 2f,
                                    expandPx = highlightExpandPx,
                                )
                                drawable.setRects(rects)
                                tv.background = drawable
                                try {
                                    snapshotFlow { fragProgress.value }.collect { fp ->
                                        val fragAlpha = when {
                                            fp <= 1f -> fp
                                            fp <= 2f -> 1f
                                            fp <= 3f -> 3f - fp
                                            else -> 0f
                                        }.coerceIn(0f, 1f)
                                        val fragScale = if (fp <= 1f) (2.0f - 1.0f * fp) else 1.0f
                                        drawable.setHighlightScale(fragScale)
                                        // Peak alpha 40% — синхронно с CustomSelectionController
                                        // (см. setMessageTextSelectable highlightColor = 0x66 = 40%).
                                        drawable.alpha = (fragAlpha * 0.4f * 255f).toInt()
                                    }
                                } finally {
                                    if (tv.background === drawable) tv.background = null
                                }
                            }
                        } else {
                            // Сбрасываем когда условие отвалилось.
                            if (fragmentRects.isNotEmpty()) fragmentRects = emptyList()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    // Reads selectionAnim.value в draw phase (deferred) — invalidates
                                    // только GPU redraw, не layout. SOMEONE сдвигается на 32dp при
                                    // progress=1, MY остаётся на месте.
                                    val bubbleShiftPx = if (!msg.isMine) selectionAnim.value * 32.dp.toPx() else 0f
                                    translationX = dragOffsetPx + bubbleShiftPx
                                },
                        ) {
                        when (msg) {
                        is Message.Text -> {
                            val time = remember(msg.id, msg.timestamp) {
                                TimeFormatter.formatBubbleTime(msg.timestamp)
                            }
                            // V1 in-place quote: text-selection активируется на КАЖДОМ выбранном бабле,
                            // независимо от количества выбранных. Не selected → setMessageTextSelectable(false).
                            // Выбор «Цитировать» в ActionMode → startQuoteInPlace → startReply (это сбросит
                            // multi-select через mutual-exclusion guard), фрагмент конкретного бабла идёт в reply.
                            val isSelected = msg.id in selectedIds
                            // Снимок «структурных» inputs configure'а — пока он не меняется, пропускаем
                            // полный view.configure(...) (тяжёлый: пересоздание drawables, swap иконок,
                            // расчёт group-corner radii). Зеркало Voice-оптимизации (см. voiceLastKeyState
                            // ниже): на рекомпозициях, вызванных cacheVersion bump'ом аватарок, swipe-state'ом
                            // соседних items, или другими «не структурными» инвалидациями — скипаем работу.
                            val textLastKeyState = remember(msg.id) {
                                mutableStateOf<List<Any?>?>(null)
                            }
                            val textLastKey = textLastKeyState.value
                            AndroidView(
                                modifier = Modifier.fillMaxWidth(),
                                factory = { ctx ->
                                    BubblesView(ctx).apply {
                                        layoutParams = fillWidthLayout()
                                        bubbleViewRef.value = this  // для computeFragmentRects (quote-highlight)
                                        // bubbleContent (визуальный баббл) — child[1] BubblesView с
                                        // WRAP_CONTENT шириной и gravity END (MY) / START (SOMEONE).
                                        // Берём максимальный right среди видимых детей, чтобы получить
                                        // правый край баббл-графики в row-координатах.
                                        addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                                            if (v is ViewGroup) {
                                                val rightMost = (0 until v.childCount).asSequence()
                                                    .map { v.getChildAt(it) }
                                                    .filter { it.visibility != View.GONE }
                                                    .maxOfOrNull { it.right } ?: 0
                                                bubbleRightPxState.value = rightMost
                                                rowWidthPxState.value = v.width
                                            }
                                        }
                                    }
                                },
                                update = { view ->
                                    val configKey = listOf(
                                        msg.isMine,
                                        msg.body,
                                        msg.status,
                                        msg.isEdited,
                                        senderName,
                                        senderAvatar,
                                        time,
                                        bubblesScheme,
                                        isFirstInGroup,
                                        isLastInGroup,
                                        avatarScheme,
                                        senderInitials,
                                        replySender,
                                        replyText,
                                        // selectionActive влияет на onReplyBlockClick лямбду:
                                        // в multi-select — handleRowTap, иначе — jump-to-original.
                                        // Без этого ключа configure не перезавёл бы лямбду при toggle режима.
                                        selectionActive,
                                        msg.replyTo?.quoteStart != null,
                                    )
                                    if (textLastKey == null || textLastKey != configKey) {
                                        view.configure(
                                            type = if (msg.isMine) BubblesView.BubbleType.MY else BubblesView.BubbleType.SOMEONE,
                                            message = msg.body,
                                            sender = senderName,
                                            time = time,
                                            avatar = senderAvatar,
                                            colorScheme = bubblesScheme,
                                            sendingState = msg.status.toBubblesState(),
                                            isFirstInGroup = isFirstInGroup,
                                            isLastInGroup = isLastInGroup,
                                            avatarColorScheme = avatarScheme,
                                            senderInitials = senderInitials,
                                            replySender = replySender,
                                            replyText = replyText,
                                            isEdited = msg.isEdited,
                                            // В multi-select reply-block обрабатывается как любая другая
                                            // точка row'а (rules 1/4): clear text-selection ИЛИ toggle checkbox.
                                            // Вне multi-select — обычный jump-to-original.
                                            onReplyBlockClick = msg.replyTo?.let { reply ->
                                                {
                                                    if (selectionActive) handleRowTap()
                                                    else onReplyBlockTap(reply)
                                                }
                                            },
                                            // TEMP(quote-icon): индикатор фрагментной цитаты (quoteStart != null).
                                            // Заменить когда в :components появятся отдельные представления Reply-секции.
                                            showQuoteIcon = msg.replyTo?.quoteStart != null,
                                            quoteIconTint = brand.accentColor(isDark),
                                        )
                                        textLastKeyState.value = configKey
                                    }
                                    // V1 — in-place quote selection (всегда активен, не привязан к variant).
                                    // CustomSelectionController через setMessageTextSelectable даёт FloatingMenu
                                    // с «Скопировать» / «Цитировать» — никакого ActionMode / TextClassifier.
                                    if (isSelected) {
                                        val accentInt = brand.accentColor(isDark)
                                        val menuItems = listOf(
                                            SelectionMenuItem("Скопировать") {
                                                val range = view.messageTextSelection ?: return@SelectionMenuItem
                                                // range.last из controller'а — exclusive end (convention
                                                // SelectionState.end), без +1.
                                                val text = msg.body.substring(range.first, range.last)
                                                val cm = view.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                cm.setPrimaryClip(android.content.ClipData.newPlainText("message", text))
                                                view.clearMessageTextSelection()
                                            },
                                            SelectionMenuItem("Цитировать") {
                                                val range = view.messageTextSelection ?: return@SelectionMenuItem
                                                view.clearMessageTextSelection()
                                                onQuoteSelected(msg.id, range.first, range.last)
                                            },
                                        )
                                        view.setMessageTextSelectable(
                                            selectable = true,
                                            menuItems = menuItems,
                                            highlightColor = (accentInt and 0x00FFFFFF) or 0x66000000,
                                            handleTint = accentInt,
                                            menuBackgroundColor = appSurface02(isDark).toArgb(),
                                            menuTextColor = appBasic(isDark, 0.9f).toArgb(),
                                            menuRippleColor = appBasic(isDark, 0.08f).toArgb(),
                                        )
                                        view.setSelectionClipRect(v1HandleClipRect.value, v1MenuClipRect.value)
                                        view.setSelectionAutoScrollCallback(v1AutoScrollCallback)
                                        view.setSelectionMenuTemporarilyHidden(v1ScrollInProgress)
                                        view.onMessageTextSelectionStart = {
                                            // Only one text-selection active at a time across all bubbles:
                                            // если уже есть active selection в ДРУГОМ бабле — clear его перед
                                            // тем как засчитать current. Это вызывает controller.clearSelection
                                            // на previous owner, который fire'ит onSelectionEnd → identity-check
                                            // увидит prevOwner === его view и обнулит state. Затем выставляем
                                            // current.
                                            val prevOwner = activeSelectionOwner.value
                                            val prevClearer = activeSelectionClearer.value
                                            if (prevOwner !== view && prevClearer != null) prevClearer()
                                            activeSelectionOwner.value = view
                                            activeSelectionClearer.value = { view.clearMessageTextSelection() }
                                        }
                                        view.onMessageTextSelectionEnd = {
                                            if (activeSelectionOwner.value === view) {
                                                activeSelectionOwner.value = null
                                                activeSelectionClearer.value = null
                                            }
                                        }
                                        // setupSelectableBubbleText: короткий tap → onBubbleTap (toggle
                                        // multi-select), long-press → CustomSelectionController (FloatingMenu).
                                        val tv = view.findMessageTextView(msg.body)
                                        tv?.let { setupSelectableBubbleText(it, accentInt) { handleRowTap() } }
                                    } else {
                                        // setMessageTextSelectable(false) делает isClickable=false →
                                        // touch-listener в setupSelectableBubbleText no-op'ит и тап
                                        // проваливается до Compose pointerInput.
                                        view.setMessageTextSelectable(false)
                                    }
                                },
                            )
                        }
                        is Message.Media -> {
                            val time = remember(msg.id, msg.timestamp) {
                                TimeFormatter.formatBubbleTime(msg.timestamp)
                            }
                            val mediaItems = remember(msg.id, msg.attachments) {
                                msg.attachments.map { att ->
                                    val isVideoItem = att.type == AttachmentType.Video
                                    MediaBubbleView.MediaItem(
                                        // MediaAttachment.thumbnail хранится как Any? — :core:model
                                        // не зависит от android (см. CLAUDE.md). Кастим обратно к Bitmap.
                                        bitmap = att.thumbnail as? Bitmap,
                                        isVideo = isVideoItem,
                                        duration = att.durationLabel,
                                        // Сам компонент использует buttonState для overlay-кнопки и
                                        // ИГНОРИРУЕТ isVideo для отрисовки. По дефолту buttonState=PLAY —
                                        // отсюда был баг «все айтемы выглядят как видео». Для фото
                                        // оверлей выключаем явно.
                                        buttonState = if (isVideoItem) MediaBubbleView.ButtonState.PLAY
                                        else MediaBubbleView.ButtonState.NONE,
                                    )
                                }
                            }
                            // V1 in-place quote для Media: на каждом выбранном бабле с непустым caption.
                            val isSelectedMedia = msg.id in selectedIds
                            // См. textLastKeyState — то же самое для Media-бабла.
                            val mediaLastKeyState = remember(msg.id) {
                                mutableStateOf<List<Any?>?>(null)
                            }
                            val mediaLastKey = mediaLastKeyState.value
                            AndroidView(
                                modifier = Modifier.fillMaxWidth(),
                                factory = { ctx ->
                                    MediaBubbleView(ctx).apply {
                                        layoutParams = fillWidthLayout()
                                        bubbleViewRef.value = this  // для computeFragmentRects (quote-highlight)
                                        addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                                            if (v is ViewGroup) {
                                                val rightMost = (0 until v.childCount).asSequence()
                                                    .map { v.getChildAt(it) }
                                                    .filter { it.visibility != View.GONE }
                                                    .maxOfOrNull { it.right } ?: 0
                                                bubbleRightPxState.value = rightMost
                                                rowWidthPxState.value = v.width
                                            }
                                        }
                                    }
                                },
                                update = { view ->
                                    val configKey = listOf(
                                        msg.isMine,
                                        mediaItems,
                                        msg.caption.orEmpty(),
                                        msg.status,
                                        msg.isEdited,
                                        senderName,
                                        senderAvatar,
                                        time,
                                        mediaScheme,
                                        isFirstInGroup,
                                        isLastInGroup,
                                        avatarScheme,
                                        senderInitials,
                                        replySender,
                                        replyText,
                                        selectionActive,
                                        msg.replyTo?.quoteStart != null,
                                    )
                                    if (mediaLastKey == null || mediaLastKey != configKey) {
                                        view.configure(
                                            type = if (msg.isMine) MediaBubbleView.BubbleType.MY else MediaBubbleView.BubbleType.SOMEONE,
                                            items = mediaItems,
                                            message = msg.caption.orEmpty(),
                                            sender = senderName,
                                            avatarBitmap = senderAvatar,
                                            time = time,
                                            sendingState = msg.status.toMediaState(),
                                            colorScheme = mediaScheme,
                                            isFirstInGroup = isFirstInGroup,
                                            isLastInGroup = isLastInGroup,
                                            avatarColorScheme = avatarScheme,
                                            senderInitials = senderInitials,
                                            replySender = replySender,
                                            replyText = replyText,
                                            isEdited = msg.isEdited,
                                            // В multi-select reply-block обрабатывается как любая другая
                                            // точка row'а (rules 1/4): clear text-selection ИЛИ toggle checkbox.
                                            // Вне multi-select — обычный jump-to-original.
                                            onReplyBlockClick = msg.replyTo?.let { reply ->
                                                {
                                                    if (selectionActive) handleRowTap()
                                                    else onReplyBlockTap(reply)
                                                }
                                            },
                                            // TEMP(quote-icon): индикатор фрагментной цитаты (quoteStart != null).
                                            // Заменить когда в :components появятся отдельные представления Reply-секции.
                                            showQuoteIcon = msg.replyTo?.quoteStart != null,
                                            quoteIconTint = brand.accentColor(isDark),
                                        )
                                        mediaLastKeyState.value = configKey
                                    }
                                    // V1 — in-place quote selection для Media с caption (всегда активен).
                                    // CustomSelectionController через setMessageTextSelectable даёт FloatingMenu
                                    // с «Скопировать» / «Цитировать» — никакого ActionMode / TextClassifier.
                                    if (isSelectedMedia && !msg.caption.isNullOrEmpty()) {
                                        val accentInt = brand.accentColor(isDark)
                                        val caption = msg.caption!!
                                        val menuItems = listOf(
                                            SelectionMenuItem("Скопировать") {
                                                val range = view.messageTextSelection ?: return@SelectionMenuItem
                                                val text = caption.substring(range.first, range.last)
                                                val cm = view.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                cm.setPrimaryClip(android.content.ClipData.newPlainText("message", text))
                                                view.clearMessageTextSelection()
                                            },
                                            SelectionMenuItem("Цитировать") {
                                                val range = view.messageTextSelection ?: return@SelectionMenuItem
                                                view.clearMessageTextSelection()
                                                onQuoteSelected(msg.id, range.first, range.last)
                                            },
                                        )
                                        view.setMessageTextSelectable(
                                            selectable = true,
                                            menuItems = menuItems,
                                            highlightColor = (accentInt and 0x00FFFFFF) or 0x66000000,
                                            handleTint = accentInt,
                                            menuBackgroundColor = appSurface02(isDark).toArgb(),
                                            menuTextColor = appBasic(isDark, 0.9f).toArgb(),
                                            menuRippleColor = appBasic(isDark, 0.08f).toArgb(),
                                        )
                                        view.setSelectionClipRect(v1HandleClipRect.value, v1MenuClipRect.value)
                                        view.setSelectionAutoScrollCallback(v1AutoScrollCallback)
                                        view.setSelectionMenuTemporarilyHidden(v1ScrollInProgress)
                                        view.onMessageTextSelectionStart = {
                                            // Only one text-selection active at a time across all bubbles:
                                            // если уже есть active selection в ДРУГОМ бабле — clear его перед
                                            // тем как засчитать current. Это вызывает controller.clearSelection
                                            // на previous owner, который fire'ит onSelectionEnd → identity-check
                                            // увидит prevOwner === его view и обнулит state. Затем выставляем
                                            // current.
                                            val prevOwner = activeSelectionOwner.value
                                            val prevClearer = activeSelectionClearer.value
                                            if (prevOwner !== view && prevClearer != null) prevClearer()
                                            activeSelectionOwner.value = view
                                            activeSelectionClearer.value = { view.clearMessageTextSelection() }
                                        }
                                        view.onMessageTextSelectionEnd = {
                                            if (activeSelectionOwner.value === view) {
                                                activeSelectionOwner.value = null
                                                activeSelectionClearer.value = null
                                            }
                                        }
                                        val tv = view.findMessageTextView(caption)
                                        tv?.let { setupSelectableBubbleText(it, accentInt) { handleRowTap() } }
                                    } else {
                                        view.setMessageTextSelectable(false)
                                    }
                                },
                            )
                        }
                        is Message.Voice -> {
                            val time = remember(msg.id, msg.timestamp) {
                                TimeFormatter.formatBubbleTime(msg.timestamp)
                            }
                            // КЛЮЧЕВО для перформанса плейбэка: collectAsState ВНУТРИ items()
                            // voice-item scope. Это restartable Composable scope LazyColumn'а —
                            // подписка на state-эмиты тут НЕ инвалидирует MessageList scope,
                            // а только этот один item. Если читать voicePlayback на уровне
                            // MessageList signature, его body пересобирался бы 30 раз/сек
                            // (List<Message> в Compose-классификации нестабилен, smart-skip
                            // не работает) — что и было источником подёргиваний при playback'е.
                            val voicePlayback by voicePlaybackFlow.collectAsState()
                            // Снимок «non-position» параметров последнего полного configure().
                            // Если на следующем update'е снимок не изменился — значит изменилась
                            // только playback-позиция, и можно идти по lightweight-пути через
                            // VoiceBubbleView.updatePlaybackPosition(...). Это резко уменьшает
                            // нагрузку Compose на 30fps плейбэк-тиках.
                            val voiceLastKeyState = remember(msg.id) {
                                mutableStateOf<List<Any?>?>(null)
                            }
                            val voiceLastKey = voiceLastKeyState.value
                            AndroidView(
                                modifier = Modifier.fillMaxWidth(),
                                factory = { ctx ->
                                    VoiceBubbleView(ctx).apply {
                                        layoutParams = fillWidthLayout()
                                        bubbleViewRef.value = this  // press-feedback scale на child[1]
                                        // bubbleContent (child[1]) — WRAP_CONTENT с gravity END (MY) / START (SOMEONE).
                                        // Тот же алгоритм, что в BubblesView/MediaBubbleView: max .right среди
                                        // visible (non-GONE) children даёт правый край баббл-графики в row-координатах.
                                        // Без этого bubbleRightPxState=0 → индикатор reply центрируется на rowWidth/2
                                        // (под баббл по z-order) и не виден при свайпе.
                                        addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                                            if (v is ViewGroup) {
                                                val rightMost = (0 until v.childCount).asSequence()
                                                    .map { v.getChildAt(it) }
                                                    .filter { it.visibility != View.GONE }
                                                    .maxOfOrNull { it.right } ?: 0
                                                bubbleRightPxState.value = rightMost
                                                rowWidthPxState.value = v.width
                                            }
                                        }
                                    }
                                },
                                update = { view ->
                                    val totalLabel = TimeFormatter.formatVoiceDuration(msg.durationMs)
                                    // Локальный снапшот: `voicePlayback` — `by` delegate (custom
                                    // getter), Kotlin не smart-cast'ит после null-check'а.
                                    val pb = voicePlayback
                                    val isCurrent = pb?.messageId == msg.id
                                    // DOWNLOADED как resting state: реального download'а у нас нет
                                    // (mock-аудио сразу «на месте»). Tap по DOWNLOADED → PLAYING
                                    // (см. VoiceBubbleViewPreviewScreen в gallery). LOADING-фаза
                                    // controller'а маппится на DOWNLOADING с loadingProgress —
                                    // спиннер при реальном download'е (сейчас не используется).
                                    val pbState = when {
                                        !isCurrent || pb == null -> VoiceBubbleView.PlaybackState.DOWNLOADED
                                        pb.state == VoicePlayback.State.LOADING -> VoiceBubbleView.PlaybackState.DOWNLOADING
                                        pb.state == VoicePlayback.State.PAUSED -> VoiceBubbleView.PlaybackState.PAUSED
                                        else -> VoiceBubbleView.PlaybackState.PLAYING
                                    }
                                    val pbPosition = if (isCurrent && pb != null) pb.position else 0f
                                    val pbLoadingProgress = if (isCurrent && pb != null) pb.loadingProgress else 0f
                                    val remainingMs = (msg.durationMs * (1f - pbPosition)).toLong()
                                    val remainingLabel = TimeFormatter.formatVoiceDuration(remainingMs)
                                    // Lightweight path: на playback-тиках (state PLAYING/PAUSED,
                                    // не меняется кадр-в-кадр) обновляем только waveform position
                                    // + duration text через `view.updatePlaybackPosition(...)`.
                                    // Полный configure() пересчитывает icon-swap, color filters,
                                    // transcribe, comment/reactions — это перебор для каждого тика.
                                    // Key — все «редкие» параметры; если они совпали с предыдущим
                                    // вызовом, значит изменилось только playback состояние (position
                                    // / remainingLabel / loadingProgress) и можно идти lightweight.
                                    val configKey = listOf(
                                        msg.isMine,
                                        pbState,
                                        msg.status,
                                        senderName,
                                        senderAvatar,
                                        time,
                                        voiceScheme,
                                        isFirstInGroup,
                                        isLastInGroup,
                                        avatarScheme,
                                        senderInitials,
                                        replySender,
                                        replyText,
                                        totalLabel,
                                        // onReplyBlockClick зависит от selectionActive (jump-to-original
                                        // vs handleRowTap); showQuoteIcon — от msg.replyTo?.quoteStart.
                                        // Без этих ключей lightweight playback-path не перевыставит
                                        // click-listener при смене multi-select / quote-флага.
                                        selectionActive,
                                        msg.replyTo?.quoteStart != null,
                                    )
                                    val lastKey = voiceLastKey
                                    if (lastKey != null && lastKey == configKey &&
                                        pbState != VoiceBubbleView.PlaybackState.DOWNLOADING) {
                                        view.updatePlaybackPosition(pbPosition, remainingLabel)
                                    } else {
                                        view.configure(
                                            type = if (msg.isMine) VoiceBubbleView.BubbleType.MY else VoiceBubbleView.BubbleType.SOMEONE,
                                            waveformBars = msg.waveform,
                                            totalDuration = totalLabel,
                                            playbackPosition = pbPosition,
                                            remainingTime = remainingLabel,
                                            playbackState = pbState,
                                            downloadProgress = pbLoadingProgress,
                                            sender = senderName,
                                            avatarBitmap = senderAvatar,
                                            time = time,
                                            sendingState = msg.status.toVoiceState(),
                                            colorScheme = voiceScheme,
                                            isFirstInGroup = isFirstInGroup,
                                            isLastInGroup = isLastInGroup,
                                            avatarColorScheme = avatarScheme,
                                            senderInitials = senderInitials,
                                            replySender = replySender,
                                            replyText = replyText,
                                            // В multi-select reply-block обрабатывается как любая
                                            // другая точка row'а (clear text-selection / toggle
                                            // checkbox). Вне multi-select — jump-to-original.
                                            // Зеркало логики из Text/Media-веток выше.
                                            onReplyBlockClick = msg.replyTo?.let { reply ->
                                                {
                                                    if (selectionActive) handleRowTap()
                                                    else onReplyBlockTap(reply)
                                                }
                                            },
                                            showQuoteIcon = msg.replyTo?.quoteStart != null,
                                            quoteIconTint = brand.accentColor(isDark),
                                        )
                                        voiceLastKeyState.value = configKey
                                    }
                                    view.onPlayPauseClick = { onVoicePlayPauseClick(msg.id) }
                                    view.onSeek = { pos -> onVoiceSeek(msg.id, pos) }
                                },
                            )
                        }
                        is Message.Link -> {
                            val time = remember(msg.id, msg.timestamp) {
                                TimeFormatter.formatBubbleTime(msg.timestamp)
                            }
                            AndroidView(
                                modifier = Modifier.fillMaxWidth(),
                                factory = { ctx ->
                                    LinkBubbleView(ctx).apply {
                                        layoutParams = fillWidthLayout()
                                        bubbleViewRef.value = this  // press-feedback scale на child[1]
                                    }
                                },
                                update = { view ->
                                    view.configure(
                                        type = if (msg.isMine) LinkBubbleView.BubbleType.MY else LinkBubbleView.BubbleType.SOMEONE,
                                        message = msg.body.orEmpty(),
                                        url = msg.url,
                                        title = msg.title,
                                        description = msg.description.orEmpty(),
                                        sender = senderName,
                                        avatarBitmap = senderAvatar,
                                        time = time,
                                        sendingState = msg.status.toLinkState(),
                                        colorScheme = linkScheme,
                                        isFirstInGroup = isFirstInGroup,
                                        isLastInGroup = isLastInGroup,
                                        avatarColorScheme = avatarScheme,
                                        senderInitials = senderInitials,
                                        replySender = replySender,
                                        replyText = replyText,
                                    )
                                    // LinkBubbleView не имеет onReplyBlockClick API (future).
                                    // При появлении reply для Link — добавить через configure().
                                },
                            )
                        }
                        is Message.CallMeet -> {
                            val time = remember(msg.id, msg.timestamp) {
                                TimeFormatter.formatBubbleTime(msg.timestamp)
                            }
                            val durationLabel = remember(msg.id, msg.durationMs) {
                                TimeFormatter.formatCallDuration(msg.durationMs ?: 0L)
                            }
                            AndroidView(
                                modifier = Modifier.fillMaxWidth(),
                                factory = { ctx ->
                                    CallMeetView(ctx).apply {
                                        layoutParams = fillWidthLayout()
                                        bubbleViewRef.value = this
                                    }
                                },
                                update = { view ->
                                    view.configure(
                                        type = if (msg.isMine) CallMeetView.Type.MY else CallMeetView.Type.SOMEONE,
                                        status = when (msg.callStatus) {
                                            CallStatus.Answered -> CallMeetView.Status.ANSWERED
                                            CallStatus.Rejected -> CallMeetView.Status.REJECTED
                                            CallStatus.Missed -> CallMeetView.Status.MISSED
                                            CallStatus.NoAnswer -> CallMeetView.Status.NO_ANSWER
                                        },
                                        isGroupCall = msg.isGroupCall,
                                        time = time,
                                        durationLabel = durationLabel,
                                        senderName = senderName,
                                        senderAvatar = senderAvatar,
                                        colorScheme = callMeetScheme,
                                        isFirstInGroup = isFirstInGroup,
                                        isLastInGroup = isLastInGroup,
                                        avatarColorScheme = avatarScheme,
                                        senderInitials = senderInitials,
                                    )
                                    // CallMeetView не имеет onReplyBlockClick API (future).
                                    // При появлении reply для CallMeet — добавить через configure().
                                },
                            )
                        }
                        is Message.System -> {
                            // Message.System is converted to RowItem.SystemRow by flattenToRows
                            // and rendered by SystemMessageRow above — unreachable in practice.
                        }
                        }
                        // Stage A: bubble pulse — basic10 (синтезированный Color.Black/White × 0.1)
                        // поверх бабла, alpha 0→1→0 за 800ms.
                        if (pulseAlpha > 0.001f) {
                            val basic10 = if (isDark) Color.White else Color.Black
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(basic10.copy(alpha = pulseAlpha * 0.1f)),
                            )
                        }
                        // Stage B (fragment overlay) теперь рендерится FragmentHighlightDrawable'ом
                        // как `messageView.background` — внутри бабла, над bg fill и под глифами.
                        // См. LaunchedEffect выше: установка drawable + snapshotFlow на fragProgress.
                        } // closes inner translated bubble Box
                        } // closes bubble row Box

                        // Checkbox conditional mounted — рендерится только когда selection активен
                        // ИЛИ анимация ещё доигрывает (`isRunning`). После завершения exit'а
                        // checkbox unmount'ится, parent overlay Box больше не учитывает его 24dp
                        // высоту в measure pass'е → row высота определяется только баблом, и не
                        // меняется при toggle selection.
                        //
                        // Slide через graphicsLayer.translationX (deferred read, draw phase, без
                        // layout invalidate). Toggle animation на тапе по баблу — через v.toggle()
                        // в update лямбде (configure уважает in-flight animation).
                        if (shouldMountCheckbox) {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .graphicsLayer {
                                    // checkbox translationX от -40dp (hidden) к 0 (visible).
                                    // -40dp = -32 (cell) - 8 (buffer от anti-alias на edge).
                                    translationX = (selectionAnim.value - 1f) * 40.dp.toPx()
                                    // Центрируем по визибл-части баббла: при scroll'е длинного
                                    // сообщения rowYAdjustmentState отражает смещение визибл-центра
                                    // от full-center баббла. graphicsLayer.translationY — pure GPU,
                                    // не триггерит re-layout.
                                    translationY = rowYAdjustmentState.value
                                }
                                .clickable(
                                    interactionSource = remember(msg.id) { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onToggleSelection(msg.id) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    val initialChecked = msg.id in selectedIds
                                    CheckboxView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.WRAP_CONTENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT,
                                        )
                                        // Снимаем native click listener — toggle уже dispatch'ится через
                                        // outer Box.clickable → viewModel.toggleSelection, отсюда
                                        // через update lambda фейк-double-click недопустим.
                                        setOnClickListener(null)
                                        isClickable = false
                                        // Initial state — без animation (snap через configure).
                                        configure(
                                            shape = CheckboxView.Shape.CIRCLE,
                                            isChecked = initialChecked,
                                            showText = false,
                                            colorScheme = checkboxScheme,
                                        )
                                    }
                                },
                                update = { v ->
                                    val target = msg.id in selectedIds
                                    // Diff в checked → запускаем animated toggle (bounce +
                                    // checkmark reveal). После toggle drawable already в нужном
                                    // state, последующий configure() видит isAnimating()=true и
                                    // не отменяет animation.
                                    if (v.getChecked() != target) {
                                        v.toggle()
                                    }
                                    v.configure(
                                        shape = CheckboxView.Shape.CIRCLE,
                                        isChecked = target,
                                        showText = false,
                                        colorScheme = checkboxScheme,
                                    )
                                },
                            )
                        }
                        } // closes if (shouldMountCheckbox)
                            }, // closes content lambda of Layout
                            measurePolicy = { measurables, constraints ->
                                val bubblePlaceable = measurables[0].measure(constraints)
                                val checkboxPlaceable = measurables.getOrNull(1)?.measure(
                                    constraints.copy(minWidth = 0, minHeight = 0),
                                )
                                val width = constraints.maxWidth
                                val height = bubblePlaceable.height
                                layout(width, height) {
                                    bubblePlaceable.place(0, 0)
                                    checkboxPlaceable?.place(
                                        x = 0,
                                        y = (height - checkboxPlaceable.height) / 2,
                                    )
                                }
                            },
                        )

                        // Реакции под бабблом обёрнуты в AnimatedVisibility — при добавлении первой
                        // (0 → 1) row плавно появляется через expandVertically + fadeIn, и бабл при
                        // этом естественно сдвигается вверх (LazyColumn layout пересчитывается из-за
                        // изменения высоты item'а). Для существующих сообщений с реакциями (visible=true
                        // с момента composition'а) AnimatedVisibility не играет enter animation —
                        // они просто появляются без задержки при скролле в viewport.
                        // graphicsLayer на самом AnimatedVisibility (а не на Box-обёртке) — чтобы
                        // expandVertically/fadeIn работали в ColumnScope; обёртка-Box ломала scope.
                        // Раскладка из BubblesViewPreviewScreen:
                        //   MY      — wrap_content, прижато к END, marginEnd = 8dp
                        //   SOMEONE — match_parent с marginStart = 40dp marginEnd = 32dp
                        //   Оба     — topMargin = 6dp
                        AnimatedVisibility(
                            visible = msg.reactions.isNotEmpty(),
                            modifier = Modifier.graphicsLayer {
                                val bubbleShiftPx = if (!msg.isMine) selectionAnim.value * 32.dp.toPx() else 0f
                                translationX = dragOffsetPx + bubbleShiftPx
                            },
                            enter = expandVertically(animationSpec = tween(280)) +
                                fadeIn(animationSpec = tween(280)),
                            exit = shrinkVertically(animationSpec = tween(200)) +
                                fadeOut(animationSpec = tween(160)),
                        ) {
                            val rxScheme = brand.reactionsColorScheme(isDark)
                            val rxType = if (msg.isMine) ReactionsView.Type.MY else ReactionsView.Type.SOMEONE
                            // Material3 ContextThemeWrapper только для View — иначе textColorPrimary
                            // системной Material темы (Theme.Template extends Theme.Material.Light, у
                            // которого textColorPrimary = #DE000000 = 87% alpha) делает эмодзи тусклыми.
                            // Та же проблема была в ContextMenuView (memory: project-context-menu-integration).
                            val themedCtxFactory: (android.content.Context) -> android.content.Context = { ctx ->
                                ContextThemeWrapper(
                                    ctx,
                                    com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar,
                                )
                            }
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = 6.dp,
                                        start = if (msg.isMine) 0.dp else 40.dp,
                                        end = if (msg.isMine) 8.dp else 32.dp,
                                    ),
                                factory = { ctx ->
                                    ReactionsView(themedCtxFactory(ctx)).apply {
                                        layoutParams = fillWidthLayout()
                                    }
                                },
                                update = { view ->
                                    view.configure(
                                        type = rxType,
                                        reactions = msg.reactions.map { r ->
                                            ReactionsView.Stack(r.emoji, r.count, r.isMine)
                                        },
                                        showAddButton = true,
                                        colorScheme = rxScheme,
                                        onReactionClick = { idx ->
                                            onReactionTap(msg.id, msg.reactions[idx].emoji)
                                        },
                                        onAddClick = { onAddReactionTap(msg.id) },
                                    )
                                },
                            )
                        }
                    } // closes Column wrapper
                    } // closes SwipeToReplyItem content
                }
            }
        }
    } // closes LazyColumn
    StickyDateOverlay(
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        listState = effectiveListState,
        dateForRow = dateForRow,
        dateRowKeys = dateRowKeys,
    )
    } // closes Box
}
