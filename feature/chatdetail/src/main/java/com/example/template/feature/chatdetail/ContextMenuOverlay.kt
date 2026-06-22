package com.example.template.feature.chatdetail

import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInQuint
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.doOnPreDraw
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
// fadeIn/fadeOut используются только для dim'а; menu Column использует только slide.
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.contextmenu.ContextMenuView
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark

/**
 * Fullscreen overlay контекстного меню сообщения. Рендерится в MainActivity ВНЕ AppScaffold,
 * чтобы не упираться в его `statusBarsPadding/navigationBarsPadding` и иметь возможность
 * затемнить статусбар + перекрасить область навбара в sheet-цвет.
 *
 * Анимации:
 * - dim — fadeIn/fadeOut
 * - меню + sheet-color полоса — slideInVertically (снизу) + fadeIn / slideOut + fadeOut
 *
 * Чтобы exit-анимация могла отыграть когда `state` уже null, держим последний non-null state
 * в `remember` и рендерим из него пока AnimatedVisibility доигрывает анимацию.
 *
 * Дизайн:
 * - Внешний Box заполняет весь экран активити (edge-to-edge), фон = 50% чёрного → dim.
 * - Тап по фону → onDismiss; back-кнопка → onDismiss (BackHandler).
 * - Содержимое прижато к низу (`Alignment.BottomCenter`): сверху меню (ContextMenuView через
 *   AndroidView), снизу полоска `sheetColor` высотой = navigationBars inset. Полоска занимает
 *   ровно область системного навбара → визуально навбар окрашен в sheet-цвет.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ContextMenuOverlay(
    state: ContextMenuState?,
    onReactionTap: (String) -> Unit,
    onAddReactionTap: () -> Unit,
    onMenuItem: (ContextMenuView.Item) -> Unit,
    onDismiss: () -> Unit,
) {
    // Помним последний non-null state, чтобы продолжать рендерить меню во время exit-анимации
    // (когда `state` уже стал null, но AnimatedVisibility ещё не завершила exit).
    // Why synchronous вместо LaunchedEffect: новое open сразу после dismiss приходит с
    // другим messageId. Если apply asynchronously — на frame N effective ещё содержит
    // СТАРОЕ значение, и `key(effective.messageId)` ниже не отработает re-mount AndroidView,
    // оставляя меню с конфигом предыдущего сообщения (баг: «меню отстаёт на один тап»).
    var lastState by remember { mutableStateOf<ContextMenuState?>(null) }
    state?.let { lastState = it }
    val effective = lastState ?: return

    val visible = state != null
    if (visible) {
        BackHandler(onBack = onDismiss)
    }

    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val sheetColor = remember(brand, isDark) { Color(brand.sheetBackground(isDark)) }
    val ctxMenuScheme = remember(brand, isDark) { brand.contextMenuColorScheme(isDark) }
    val density = LocalDensity.current
    val navBarHeight = with(density) {
        WindowInsets.navigationBars.getBottom(density).toDp()
    }

    // Один AnimatedVisibility с EnterTransition.None/ExitTransition.None на корне —
    // анимации задаются per-child через Modifier.animateEnterExit. Это даёт независимые
    // тайминги/жесты для dim'а (fade) и меню (slide + fade).
    //
    // MutableTransitionState(initialState=false) обязателен: AnimatedVisibility со старта
    // с visible=true НЕ играет enter-анимацию. Через transitionState стартуем с false и
    // переключаем targetState=true в первой композиции — AnimatedVisibility ловит переход
    // false→true и играет enter.
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible
    AnimatedVisibility(
        visibleState = transitionState,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Dim. .clickable без ripple/indication — тапы внутри меню (по реакциям/items)
            // консьюмят событие до этого Box'а, поэтому только клик «мимо» дёргает onDismiss.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .animateEnterExit(
                        enter = fadeIn(animationSpec = tween(240)),
                        exit = fadeOut(animationSpec = tween(240)),
                    )
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            // Двухфазная анимация enter'а:
            //   Фаза 1 (0-280ms): Column slide+fade — меню «открывается» целиком (reactions pill
            //   делает параллельный собственный fade-in, items внутри menuSheet невидимы —
            //   alpha=0 выставлен в doOnPreDraw до первой отрисовки).
            //   Фаза 2 (с PHASE_2_DELAY=240ms): items внутри menuSheet появляются сверху вниз
            //   с лёгким сдвигом из-за левого края, stagger между ними. См. animateMenuItemsStagger.
            // На exit: slide + fade (обе одной длительности, иначе content успевает стать
            // прозрачным посреди slide'а и видно «прыжок» обрезки по экрану).
            //
            // Swipe-to-dismiss: drag вниз сдвигает Column через Modifier.offset с Animatable.
            // Если на release сдвиг > 40% высоты Column — animate до полной высоты и onDismiss
            // (AnimatedVisibility доиграет fadeOut dim'а). Иначе — snap-back через spring.
            // Drag вверх клампится в 0 (нельзя «вытащить» меню выше нормали).
            val dragOffset = remember { Animatable(0f) }
            val coroutineScope = rememberCoroutineScope()
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .offset { IntOffset(0, dragOffset.value.roundToInt()) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                val height = size.height.toFloat()
                                val threshold = height * 0.4f
                                coroutineScope.launch {
                                    if (dragOffset.value > threshold) {
                                        dragOffset.animateTo(
                                            targetValue = height,
                                            animationSpec = tween(180),
                                        )
                                        onDismiss()
                                    } else {
                                        dragOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(),
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    dragOffset.animateTo(0f, animationSpec = spring())
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val target = (dragOffset.value + dragAmount).coerceAtLeast(0f)
                                coroutineScope.launch { dragOffset.snapTo(target) }
                            },
                        )
                    }
                    .animateEnterExit(
                        // Enter: slideIn 280ms default easing (быстро приезжает) + fadeIn 320ms
                        // EaseInQuint (alpha=t^5: половину времени почти прозрачен, резкий скачок
                        // opacity в последней трети).
                        // Exit: slide и fade параллельно. fadeOut 260ms — alpha доходит до ~0
                        // примерно к 65% slide'а (≈230ms из 360).
                        //   - slideOut 360ms, default easing
                        //   - fadeOut 260ms, EaseOutCubic: α=0.34 к 78ms, α=0.125 к 130ms,
                        //     α=0.03 к 182ms, α=0 к 260ms.
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(280),
                        ) + fadeIn(animationSpec = tween(320, easing = EaseInQuint)),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(360),
                        ) + fadeOut(animationSpec = tween(260, easing = EaseOutCubic)),
                    ),
            ) {
                // key(effective.messageId): при новом open до завершения exit-анимации
                // AnimatedVisibility отменяет exit и не дисполит детей — AndroidView
                // переиспользовался бы со старым factory(...) и старым configure(...) → меню
                // показывало бы данные предыдущего сообщения. key форсит unmount+remount при
                // смене messageId, новый factory зовётся со свежим effective.
                key(effective.messageId) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { ctx ->
                            // Material3 ContextThemeWrapper только для View — иначе textColorPrimary
                            // системной Material темы (alpha 87%) делает эмодзи тусклыми. См. memory
                            // project-context-menu-integration.
                            val themedCtx = ContextThemeWrapper(
                                ctx,
                                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar,
                            )
                            ContextMenuView(themedCtx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                )
                                // configure() ВЫЗЫВАЕТСЯ ЗДЕСЬ, А НЕ В UPDATE. configure() через
                                // renderItems() делает `itemsContainer.removeAllViews()` и
                                // пересоздаёт items. Если она вызывается в update, Compose дёргает
                                // её несколько раз за жизнь меню (рекомпозиции из AnimatedVisibility
                                // transition'а) — каждая пересоздаёт items и убивает наши
                                // staggered-анимации, оставляя их на orphaned views (parent=null,
                                // видно в логе). За свежий конфиг на каждое новое сообщение
                                // отвечает key(effective.messageId) выше — он форсит re-mount.
                                configure(
                                    type = effective.type,
                                    mode = effective.mode,
                                    isPinned = effective.isPinned,
                                    messageKind = effective.messageKind,
                                    isSending = effective.isSending,
                                    canReport = effective.canReport,
                                    colorScheme = ctxMenuScheme,
                                )
                                onReactionClicked = { e -> onReactionTap(e) }
                                onAddReactionClicked = { onAddReactionTap() }
                                onItemClicked = { i -> onMenuItem(i) }
                                // doOnPreDraw (one-shot) — выставляет items.alpha=0 + translationX
                                // перед первой отрисовкой и запускает staggered анимацию.
                                doOnPreDraw { animateMenuContentAppear(it as ContextMenuView) }
                            }
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(navBarHeight)
                        .background(sheetColor),
                )
            }
        }
    }
}

/**
 * Фаза 2 анимации: items внутри menuSheet появляются сверху вниз с лёгким сдвигом из-за
 * левого края. Запускается с задержкой [PHASE_2_DELAY] — это даёт фазе 1 (Column slide+fade)
 * полностью завершиться. До этого items невидимы (alpha=0 выставлен здесь же).
 *
 * Items простые (icon + text), 8-9 штук. Без `withLayer()` — на коротких animations 8+
 * hardware layer'ов давали GPU memory нагрузку + setup overhead. Сейчас фаза 1 уже отработала,
 * items анимируются БЕЗ concurrent Column-slide, нагрузка минимальная.
 *
 * ContextMenuView (LinearLayout vertical) → child[2] = menuSheet → child[1] = itemsContainer →
 * дети = item rows. Структура из `:components/contextmenu/ContextMenuView.kt` (init).
 * Хрупко к изменениям компонента, но android-components мы не трогаем (см. memory).
 */
// Phase 2 (анимация items) стартует с этой задержкой от t=0 (от начала Column slide+fade).
// 200ms — Column на ~70% открылся, items начинают появляться следом не блокируя восприятие
// общей прозрачности меню (Column.fadeIn = 280ms).
private const val PHASE_2_DELAY = 200L

private fun animateMenuContentAppear(view: ContextMenuView) {
    val reactions = view.getChildAt(0)
    val menuSheet = view.getChildAt(2) as? ViewGroup ?: return
    val itemsContainer = menuSheet.getChildAt(1) as? ViewGroup ?: return
    val translateXStart = -view.resources.displayMetrics.density * 24f // -24dp
    val interp = DecelerateInterpolator()
    val itemDuration = 180L
    val itemStagger = 30L      // 30ms — следующий стартует когда предыдущий только начал
    val reactionsDuration = 280L  // совпадает с Column slide+fade

    // Reactions pill — собственный fade-in ПАРАЛЛЕЛЬНО с Column.slide+fade (без startDelay).
    // Длительность совпадает с Column'ом → реакции «материализуются» одновременно с открытием
    // меню. Композитный с Column.fadeIn даёт реакциям squared-кривую (чуть медленнее
    // относительно остальных частей Column), но визуально воспринимается как часть общего
    // опенинга.
    if (reactions != null) {
        reactions.alpha = 0f
        reactions.animate()
            .alpha(1f)
            .setDuration(reactionsDuration)
            .setInterpolator(interp)
            .start()
    }

    // Items внутри menuSheet — каждый: alpha 0→1 + translationX -24dp→0, со stagger'ом.
    for (i in 0 until itemsContainer.childCount) {
        val item = itemsContainer.getChildAt(i)
        item.alpha = 0f
        item.translationX = translateXStart
        item.animate()
            .alpha(1f)
            .translationX(0f)
            .setStartDelay(PHASE_2_DELAY + i * itemStagger)
            .setDuration(itemDuration)
            .setInterpolator(interp)
            .start()
    }
}

