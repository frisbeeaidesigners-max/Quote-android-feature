package com.example.template

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.example.template.core.ui.LocalBitmapCache
import com.example.template.core.ui.hosts.AudioPanelHost
import com.example.template.core.ui.hosts.BottomTabsHost
import com.example.template.feature.calls.CallsScreen
import com.example.template.feature.calls.CallsViewModel
import com.example.template.feature.chats.ChatsScreen
import com.example.template.feature.chats.ChatsViewModel
import com.example.template.feature.spaces.SpacesScreen
import com.example.template.feature.spaces.SpacesViewModel
import kotlinx.coroutines.delay

private const val TAB_COUNT = 4
private const val PRE_WARM_DELAY_MS = 600L
private const val BADGE_REVEAL_STAGGER_MS = 180L
/**
 * Длительность tab-slide'а для расстояния в одну позицию (Chats↔Spaces, Spaces↔Calls).
 * Для прыжка через табы (Chats↔Calls = расстояние 2) фактическая длительность —
 * `TAB_SLIDE_STEP_MS × distance`. Так визуальная скорость анимации остаётся постоянной
 * вне зависимости от того, на сколько позиций пользователь прыгнул.
 */
private const val TAB_SLIDE_STEP_MS = 220

@Composable
fun MainScaffold(
    onChatClick: (String) -> Unit,
    onProfileClick: () -> Unit = {},
    showBottomTabs: Boolean = true,
    /**
     * Когда `true` — стартует фоновый прогрев остальных табов (Spaces/Calls) с шагом
     * [PRE_WARM_DELAY_MS]. Передаём `false` пока виден splash, чтобы тяжёлая композиция
     * AndroidView'ев не дёргала main-тред во время fadeOut splash'а и тот шёл плавно.
     */
    enablePrewarm: Boolean = true,
    /** Кодовое имя бренда из BuildConfig — пробрасывается вниз в CallsScreen. */
    brandCodename: String = "foxtrot",
    /** Триггер открытия экрана исходящего звонка. Вызывается из CallsScreen на «Новая встреча». */
    onStartCall: (com.example.template.core.model.CallContext) -> Unit = {},
) {
    val container = LocalAppContainer.current
    val repo = container.repository
    val voiceController = container.voicePlaybackController
    // КЛЮЧЕВО: voicePlayback здесь НЕ читаем (без collectAsState на уровне MainScaffold).
    // Подписка изолирована в `MainAudioPanelSlot` ниже — её recomposition не каскадит на
    // тяжёлый ChatListHost / SpacesScreen body.
    val bitmapCache = LocalBitmapCache.current
    val cacheVersion by bitmapCache.version

    val currentUser by repo.currentUser.collectAsState()
    val profileAvatar = remember(currentUser.avatar.imageAsset, bitmapCache, cacheVersion) {
        bitmapCache.get(currentUser.avatar.imageAsset)
    }
    val p2pChats by repo.p2pChats.collectAsState()
    val spaceChats by repo.allSpaceChats.collectAsState()
    // В бейдж попадают: непрочитанные не-замьюченные чаты ИЛИ любые чаты с упоминанием.
    // Mention пробивает mute — это явный сигнал, что пользователь нужен здесь, независимо
    // от настроек уведомлений по чату.
    val chatsBadge = p2pChats.count { it.mention || (!it.muted && it.unreadCount > 0) }
    val spacesBadge = spaceChats.count { it.mention || (!it.muted && it.unreadCount > 0) }
    val rawBadgeCounts = listOf(chatsBadge, spacesBadge, 0, 0)

    // Раскрытие бейджей по очереди: пока `revealedBadges` не достигнет индекса, отдаём 0
    // вместо реального счётчика. BottomTabs не рисует бейдж при count==0, а на переходе 0→N
    // отыгрывает уже существующую spring pop-in анимацию. Профиль (индекс 3) исключаем —
    // у него бейджа нет в принципе.
    var revealedBadges by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        repeat(TAB_COUNT - 1) { i ->
            revealedBadges = i + 1
            delay(BADGE_REVEAL_STAGGER_MS)
        }
    }
    val badgeCounts = rawBadgeCounts.mapIndexed { i, n ->
        if (i == TAB_COUNT - 1) 0 else if (i < revealedBadges) n else 0
    }

    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    // Какие вкладки уже композировались. Стартуем с открытой Chats; остальные постепенно
    // прогреваются в фоне (PRE_WARM_DELAY_MS на каждую), чтобы Compose не делал всю работу
    // одновременно на холодном старте. Обязательно включаем сюда `currentTab`: после возврата
    // из ChatDetail (NavHost диспозит Main) этот блок инициализируется заново, а `currentTab`
    // восстанавливается из saveable. Без этого Spaces/Calls/Profile показывают пустой экран
    // ~600ms до следующего тика прогрева.
    val mountedTabs = remember { mutableStateOf(setOf(0, currentTab)) }

    LaunchedEffect(enablePrewarm) {
        if (!enablePrewarm) return@LaunchedEffect
        // Прогреваем только реальные табы (0,1,2). Index 3 — Profile-оверлей, монтируется
        // лениво в MainActivity при первом открытии. Гейт enablePrewarm не даёт стартовать
        // пока виден splash — иначе тяжёлая композиция Spaces/Calls упиралась бы в fadeOut.
        for (i in 1 until TAB_COUNT - 1) {
            delay(PRE_WARM_DELAY_MS)
            mountedTabs.value = mountedTabs.value + i
        }
    }

    // animateFloatAsState давал бы фиксированный 220ms независимо от того, перепрыгиваешь ли
    // ты с Chats на соседний Spaces или сразу на Calls. Через Animatable + LaunchedEffect
    // подменяю durationMillis на TAB_SLIDE_STEP_MS × distance: визуальная скорость слайда
    // одинакова, время растёт пропорционально расстоянию. Distance считаем по текущему
    // visual-положению animatedTab.value (а не по последнему таргету) — корректно
    // отрабатывает прерывание анимации повторным тапом.
    val animatedTab = remember { Animatable(currentTab.toFloat()) }
    LaunchedEffect(currentTab) {
        val source = animatedTab.value
            .let { kotlin.math.round(it).toInt() }
            .coerceIn(0, TAB_COUNT - 1)
        val distance = kotlin.math.abs(currentTab - source).coerceAtLeast(1)
        animatedTab.animateTo(
            targetValue = currentTab.toFloat(),
            animationSpec = tween(
                durationMillis = TAB_SLIDE_STEP_MS * distance,
                easing = LinearOutSlowInEasing,
            ),
        )
    }

    // ViewModels создаются по запросу и переиспользуются — экраны живут в композиции после
    // первого монтирования.
    val chatsVm = remember(repo) { ChatsViewModel(repo) }
    val spacesVm = remember(repo) { SpacesViewModel(repo) }
    val callsVm = remember(repo) { CallsViewModel(repo) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        repeat(TAB_COUNT) { index ->
            if (index in mountedTabs.value) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // graphicsLayer вместо offset: переход — чистый GPU-transform без
                        // повторных layout-пассов. Layout тяжёлых AndroidView (ChatListItemView,
                        // HeadersView, MessagePanelView) больше не дёргается на каждом кадре
                        // анимации — это и был источник «задержки» после тапа по табу.
                        .graphicsLayer { translationX = (index - animatedTab.value) * widthPx }
                ) {
                    // AudioPanel под главным хедером — только в Chats/Spaces, не в Calls/Profile.
                    // Внутри слота — отдельный composable, который сам подписывается на
                    // voicePlayback. Recomposition этого слота не каскадит на ChatsScreen body.
                    val belowHeaderAudioPanel: @Composable () -> Unit = {
                        MainAudioPanelSlot(repo, voiceController)
                    }
                    when (index) {
                        0 -> ChatsScreen(
                            viewModel = chatsVm,
                            onChatClick = onChatClick,
                            belowHeader = belowHeaderAudioPanel,
                        )
                        1 -> SpacesScreen(
                            viewModel = spacesVm,
                            onChatClick = onChatClick,
                            belowHeader = belowHeaderAudioPanel,
                        )
                        2 -> CallsScreen(
                            viewModel = callsVm,
                            brandCodename = brandCodename,
                            onStartCall = onStartCall,
                        )
                        // index 3 (Profile) — оверлей в MainActivity, не таб.
                    }
                }
            }
        }
        if (showBottomTabs) {
            BottomTabsHost(
                selectedTabIndex = currentTab,
                onTabSelected = { newTab ->
                    if (newTab == TAB_COUNT - 1) {
                        // Profile открывается как полноэкранный оверлей в MainActivity,
                        // currentTab не трогаем — после закрытия профиля останется текущая вкладка.
                        onProfileClick()
                        return@BottomTabsHost
                    }
                    if (newTab !in mountedTabs.value) {
                        mountedTabs.value = mountedTabs.value + newTab
                    }
                    currentTab = newTab
                },
                profileAvatar = profileAvatar,
                badgeCounts = badgeCounts,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Изолированная подписка на voice playback для AudioPanel под главным хедером.
 * Composable scope — узкий, recomposition 30 раз/сек не каскадит на ChatsScreen body
 * (где тяжёлый ChatListHost). См. большой комментарий в MainScaffold body.
 */
@Composable
private fun MainAudioPanelSlot(
    repo: com.example.template.core.data.MessengerRepository,
    controller: com.example.template.core.ui.hosts.VoicePlaybackController,
) {
    val playback by controller.voicePlayback.collectAsState()
    val current = playback
    // Удерживаем последнее non-null значение, чтобы во время exit-анимации
    // AnimatedVisibility'у было что рендерить (после null сам StateFlow перестаёт
    // поставлять данные, без удержания content исчезнет до окончания shrink+fade).
    val lastPlayback = remember { mutableStateOf<com.example.template.core.ui.hosts.VoicePlayback?>(null) }
    if (current != null) lastPlayback.value = current
    val displayPlayback = lastPlayback.value
    AnimatedVisibility(
        visible = current != null,
        enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) +
            fadeIn(tween(150)),
        exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) +
            fadeOut(tween(150)),
    ) {
        if (displayPlayback != null) {
            AudioPanelHost(
                playback = displayPlayback,
                repository = repo,
                controller = controller,
            )
        }
    }
}
