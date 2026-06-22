package com.example.template

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import android.view.WindowManager
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.template.core.ui.LocalAppBrand
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.template.core.ui.AppScaffold
import com.example.template.core.ui.AppTheme
import com.example.template.core.ui.LocalBitmapCache
import com.example.template.core.ui.LocalThemeToggle
import com.example.template.core.ui.LocalQuotePickerVariant
import com.example.template.feature.chatdetail.ChatDetailScreen
import com.example.template.feature.chatdetail.ChatDetailViewModel
import com.example.template.feature.chatdetail.ContextMenuOverlay
import com.example.template.feature.chatdetail.quotepicker.QuotePickerFullScreen
import com.example.template.feature.profile.ProfileEditScreen
import com.example.template.feature.profile.ProfileScreen
import com.example.template.feature.profile.ProfileViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // System scrim plавно перекрашивается при смене isAppearanceLight* — отключаем,
        // чтобы фон системных баров мгновенно следовал за цветом нашего Compose-фона.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        val container = (application as TemplateApp).container
        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDark by rememberSaveable { mutableStateOf(systemDark) }
            val toggleTheme: () -> Unit = { isDark = !isDark }

            DisposableEffect(isDark) {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
                onDispose {}
            }

            CompositionLocalProvider(
                LocalAppContainer provides container,
                LocalBitmapCache provides container.bitmapCache,
                LocalThemeToggle provides toggleTheme,
                LocalQuotePickerVariant provides container.quotePickerVariant,
            ) {
                AppTheme(isDark = isDark) {
                    // SplashOverlay рендерится поверх всего на первой композиции; main UI ниже
                    // гейтится через mainUiReady — иначе тяжёлые AndroidView (ChatListItemView,
                    // MessagePanelView и т.д.) композятся в первом же кадре и оттягивают момент,
                    // когда splash станет видим. Flip mainUiReady=true в LaunchedEffect — он
                    // выполняется ПОСЛЕ первого draw'а, поэтому splash успевает показаться, а
                    // основной UI начинает монтироваться уже за ним.
                    // rememberSaveable: на rotation сохраняются — splash не показывается заново
                    // и MainScaffold рендерится сразу без 700ms задержки. На первом запуске оба
                    // false, через 1 кадр mainUiReady=true, через 700ms+анимация splashGone=true.
                    var mainUiReady by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(Unit) { mainUiReady = true }
                    // splashGone флипается в SplashOverlay.onGone после завершения обеих
                    // fadeOut-анимаций. Используется как enablePrewarm для MainScaffold —
                    // без splash'а на экране main-тред свободен и pre-warm Spaces/Calls
                    // не дёргает анимацию (раньше pre-warm стартовал параллельно с fadeOut'ом
                    // splash'а, и тяжёлые AndroidView'ы дёргали main-тред).
                    var splashGone by rememberSaveable { mutableStateOf(false) }
                    // Используем state-driven overlay вместо NavHost: MainScaffold остаётся
                    // в композиции навсегда (нет дорогих повторных сборок ChatListHost +
                    // десятков AndroidView/ChatListItemView на возврат). ChatDetail рисуется
                    // поверх, когда openChatId != null. saveable, чтобы пережил configChange.
                    var openChatId by rememberSaveable { mutableStateOf<String?>(null) }
                    // Profile открывается из BottomTabs как полноэкранный оверлей поверх
                    // MainScaffold (как QuotePickerFullScreen) — БЕЗ slide-анимации, мгновенно.
                    // showBottomTabs=false когда оверлей виден.
                    var profileOpen by rememberSaveable { mutableStateOf(false) }
                    // ProfileEdit — отдельная страница поверх ProfileScreen (как Profile поверх MainScaffold).
                    // Кнопка Edit в хедере профиля переключает на этот оверлей; кнопки Save/Back возвращают.
                    var profileEditOpen by rememberSaveable { mutableStateOf(false) }
                    // Контекст активного исходящего звонка. null = звонка нет.
                    // Открывается из CallsScreen («Новая встреча») или ChatHeader (P2P/Group) через
                    // MainScaffold.onStartCall. BottomTabs скрываются пока callContext != null.
                    var callContext by remember {
                        mutableStateOf<com.example.template.core.model.CallContext?>(null)
                    }
                    val profileVm = remember { ProfileViewModel(container.repository) }
                    // При закрытии чата явно прячем клавиатуру: Compose ничего не
                    // диспозит (MessagePanel живёт в MainScaffold-overlay'е), фокус
                    // EditText'а сохраняется → IME оставалась видимой при возврате
                    // на список чатов.
                    val closeChat: () -> Unit = {
                        WindowInsetsControllerCompat(window, window.decorView)
                            .hide(WindowInsetsCompat.Type.ime())
                        openChatId = null
                    }
                    // Стабилизируем колбэк через remember: без этого каждая per-frame рекомпозиция
                    // обёртки (триггерится чтением progress > 0f в composition scope) создавала бы
                    // новую лямбду → MainScaffold считался бы нестабильным и перевыполнял своё тело
                    // 60×/сек во время slide-анимации. На закрытии чата фон становится главным
                    // фокусом — этот лишний main-thread cost проявлялся как рывки на параллаксе.
                    val onChatClick: (String) -> Unit = remember { { id -> openChatId = id } }
                    // VM поднята наружу AppScaffold чтобы ContextMenuOverlay (рендерится тут
                    // на root-уровне, ВНЕ AppScaffold) мог наблюдать contextMenu state. Сам
                    // overlay требует fullscreen без statusBars/navigationBars padding'а.
                    val chatVm: ChatDetailViewModel? = openChatId?.let { id ->
                        remember(id) { ChatDetailViewModel(id, container.repository, container.voicePlaybackController) }
                    }
                    // Удерживаем последний non-null VM на время slide-out анимации:
                    // chatVm становится null синхронно с openChatId=null, но AnimatedVisibility
                    // ещё показывает контент пока проигрывает exit. Без этого внутри лямбды
                    // ChatDetailScreen падал бы (viewModel = null).
                    val lastChatVm = remember { mutableStateOf<ChatDetailViewModel?>(null) }
                    LaunchedEffect(chatVm) { if (chatVm != null) lastChatVm.value = chatVm }
                    val visibleChatVm = chatVm ?: lastChatVm.value
                    val ctx = LocalContext.current
                    // MessagePanel воспроизводит запись голосового — перекрашиваем
                    // системную navigation-bar область (под home-индикатором) в акцент.
                    // State поднят сюда чтобы overlay-полоса лежала ВНЕ AppScaffold
                    // (тот съедает navigationBarsPadding внутри content'а).
                    var panelVoiceMode by remember { mutableStateOf(false) }
                    // animProgress поднят на root scope (раньше жил внутри BoxWithConstraints) —
                    // чтобы voice-accent полоса под home-индикатором, которая рисуется ВНЕ
                    // AppScaffold, могла применить тот же translationX и уехать вправо
                    // синхронно с ChatDetail. Иначе полоса просто моргала transparent на старте
                    // slide'а. Семантика та же: 0=чат закрыт, 1=открыт, между — slide.
                    val animProgress = remember {
                        Animatable(if (chatVm != null) 1f else 0f)
                    }
                    // Отдельный Boolean удерживает ChatDetail в композиции на время exit-анимации.
                    // Меняется 2 раза за цикл (true в начале, false в конце close-animateTo).
                    var keepMountedForExit by remember { mutableStateOf(false) }

                    LaunchedEffect(chatVm != null) {
                        if (chatVm != null) {
                            keepMountedForExit = true
                            animProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(280, easing = FastOutSlowInEasing),
                            )
                        } else {
                            animProgress.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(280, easing = FastOutSlowInEasing),
                            )
                            keepMountedForExit = false
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                      if (mainUiReady) {
                        AppScaffold {
                            // Единый progress драйвит две translation'ы: список чатов чуть уезжает
                            // влево (параллакс), ChatDetail заезжает справа. translationX — pure
                            // GPU, без remeasure тяжёлых AndroidView. animProgress.value читаем
                            // ТОЛЬКО внутри graphicsLayer-лямбд (draw scope) — state read там
                            // триггерит invalidate layer без рекомпозиции.
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val fullWidthPx = constraints.maxWidth.toFloat()
                                // 20% ширины — «чуть-чуть». Подкрутить тут если ощущения не те.
                                val parallaxShiftPx = fullWidthPx * 0.20f

                                // chatVm != null — синхронный гейт монтирования на open (мгновенно,
                                // в той же композиции). keepMountedForExit удерживает ChatDetail на
                                // close-анимации, пока animateTo(0f) не вернётся.
                                val showChat = chatVm != null || keepMountedForExit

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { translationX = -parallaxShiftPx * animProgress.value },
                                ) {
                                    MainScaffold(
                                        onChatClick = onChatClick,
                                        onProfileClick = { profileOpen = true },
                                        enablePrewarm = splashGone,
                                        showBottomTabs = openChatId == null && !profileOpen && callContext == null,
                                        brandCodename = BuildConfig.BRAND_CODENAME,
                                        onStartCall = { ctx -> callContext = ctx },
                                    )
                                }
                                if (showChat && visibleChatVm != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { translationX = fullWidthPx * (1f - animProgress.value) },
                                    ) {
                                        // BackHandler регистрируем только когда чат реально открыт,
                                        // не на exit-фазе — second back во время slide-out не нужен.
                                        if (chatVm != null) BackHandler(onBack = closeChat)
                                        ChatDetailScreen(
                                            viewModel = visibleChatVm,
                                            onBack = closeChat,
                                            onVoiceModeChange = { panelVoiceMode = it },
                                            onStartCall = { ctx -> callContext = ctx },
                                        )
                                    }
                                }
                            }
                        }
                        // Voice-mode accent над home-индикатором. Полоса высотой = nav-bar
                        // inset, рисуется ПОВЕРХ AppScaffold (т.е. поверх backgroundBase,
                        // который тот красит до padding'а). Цвет на toggle меняется мгновенно:
                        // MessagePanelView.startVoiceRecording() сам не аниматит переход
                        // (просто setBackgroundColor + visibility flip), и любая color-аниматация
                        // тут читается как лаг.
                        //
                        // На back из чата применяем ТОТ ЖЕ translationX, что у ChatDetail
                        // (`screenWidthPx * (1f - animProgress.value)`) — полоса уезжает вправо
                        // вместе со страницей переписки. На open чата (animProgress 0→1) она
                        // въезжает справа — но voiceMode=false при открытии, цвет прозрачный,
                        // визуально это no-op.
                        val brand = LocalAppBrand.current
                        val navBarHeight = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding()
                        val screenWidthPx = with(LocalDensity.current) {
                            LocalConfiguration.current.screenWidthDp.dp.toPx()
                        }
                        val stripColor = if (panelVoiceMode) Color(brand.accentColor(isDark)) else Color.Transparent
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(navBarHeight)
                                .graphicsLayer { translationX = screenWidthPx * (1f - animProgress.value) }
                                .background(stripColor),
                        )
                        // ContextMenuOverlay рендерится ВНЕ AppScaffold чтобы dim дотягивался
                        // до статусбара, а sheet-color полоска лежала ровно под навбаром
                        // (AppScaffold обрезает overlay'и своими statusBars/navigationBars padding'ами).
                        // state nullable пробрасываем как есть — overlay сам держит последний
                        // non-null state в remember чтобы exit-анимация могла отыграть.
                        if (chatVm != null) {
                            val ctxMenuState by chatVm.contextMenu.collectAsState()
                            ContextMenuOverlay(
                                state = ctxMenuState,
                                onReactionTap = chatVm::toggleReaction,
                                onAddReactionTap = chatVm::dismissContextMenu,
                                onMenuItem = { item -> chatVm.onMenuItem(item, ctx) },
                                onDismiss = chatVm::dismissContextMenu,
                            )
                        }
                        // Fullscreen quote picker — рендерится ВНЕ AppScaffold (так же
                        // как ContextMenuOverlay), чтобы фон тянулся до системных баров
                        // (Activity-окно уже настроено в onCreate: edge-to-edge + appearance
                        // flags). Внутри Dialog'а аналогичные настройки не применялись на ряде
                        // ROM'ов, отсюда невидимые часы и перекрытые nav-кнопки.
                        if (chatVm != null) {
                            val pickerVisible by chatVm.quotePickerVisible.collectAsState()
                            val replyCtx by chatVm.replyContext.collectAsState()
                            val messages by chatVm.messages.collectAsState()
                            // IME-z-order всегда выше app — Compose-overlay не может визуально
                            // перекрыть системную клаву. Поэтому на open прячем IME, на close
                            // восстанавливаем если она БЫЛА открыта (стандартный pattern Telegram
                            // / WhatsApp / Signal). Фокус EditText'а у MessagePanel сохраняется,
                            // .show(ime()) сам вернёт клаву к нему.
                            DisposableEffect(pickerVisible) {
                                if (pickerVisible) {
                                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                                    val wasImeVisible = ViewCompat.getRootWindowInsets(window.decorView)
                                        ?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
                                    // Меняем softInputMode на STATE_ALWAYS_HIDDEN, сохраняя
                                    // оригинальные ADJUST_RESIZE-биты. Иначе на resume window
                                    // получает focus и Android спонтанно поднимает IME для
                                    // focused EditText'а (MessagePanel или SelectableEditText).
                                    // STATE_ALWAYS_HIDDEN говорит «не показывать IME ни при
                                    // каком focus-event на этом окне», что и убирает flash.
                                    val originalSoftInputMode = window.attributes.softInputMode
                                    val adjustBits = originalSoftInputMode and
                                        WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
                                    window.setSoftInputMode(
                                        adjustBits or
                                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                                    )
                                    controller.hide(WindowInsetsCompat.Type.ime())
                                    // Belt-and-suspenders: на случай если softInputMode на каком-
                                    // то ROM'е не отрабатывает, ON_RESUME observer прячет повторно.
                                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                            controller.hide(WindowInsetsCompat.Type.ime())
                                        }
                                    }
                                    lifecycle.addObserver(observer)
                                    onDispose {
                                        lifecycle.removeObserver(observer)
                                        // Восстанавливаем softInputMode ДО show(ime()) — иначе
                                        // ALWAYS_HIDDEN может перебить программный show.
                                        window.setSoftInputMode(originalSoftInputMode)
                                        if (wasImeVisible) controller.show(WindowInsetsCompat.Type.ime())
                                    }
                                } else {
                                    onDispose { }
                                }
                            }
                            val cv = replyCtx
                            if (pickerVisible && cv != null) {
                                val originalMessage = messages.firstOrNull { it.id == cv.originalId }
                                LaunchedEffect(originalMessage) {
                                    if (originalMessage == null) chatVm.dismissQuotePicker()
                                }
                                if (originalMessage != null) {
                                    val cacheVersion by container.bitmapCache.version
                                    val senderPersona = remember(originalMessage.senderId) {
                                        chatVm.personaForUser(originalMessage.senderId)
                                    }
                                    val senderAvatar = remember(senderPersona?.avatarAsset, cacheVersion) {
                                        senderPersona?.avatarAsset?.let { container.bitmapCache.get(it) }
                                    }
                                    val variantFlow = LocalQuotePickerVariant.current
                                    val variant by variantFlow.collectAsState()
                                    QuotePickerFullScreen(
                                        variant = variant,
                                        message = originalMessage,
                                        senderPersona = senderPersona,
                                        senderAvatar = senderAvatar,
                                        isMine = originalMessage.isMine,
                                        initialStart = cv.quoteStart ?: 0,
                                        initialEnd = cv.quoteEnd ?: 0,
                                        onConfirm = { start, end ->
                                            if (start == end) chatVm.clearQuote()
                                            else chatVm.setQuote(start, end)
                                            chatVm.dismissQuotePicker()
                                        },
                                        onDismiss = { chatVm.dismissQuotePicker() },
                                        onCancelReply = { chatVm.dismissReplyContext() },
                                    )
                                }
                            }
                        }
                        // Profile-оверлей — рендерится ВНЕ AppScaffold (как QuotePickerFullScreen),
                        // ProfileScreen сам делает .fillMaxSize().background(...).statusBarsPadding()
                        // .navigationBarsPadding() → фон тянется edge-to-edge, контент инсетится.
                        // Без slide-анимации: open/close мгновенный, как «отдельная страница».
                        if (profileOpen) {
                            ProfileScreen(
                                viewModel = profileVm,
                                onClose = {
                                    profileOpen = false
                                    profileEditOpen = false
                                },
                                onEdit = { profileEditOpen = true },
                            )
                        }
                        if (profileOpen && profileEditOpen) {
                            ProfileEditScreen(
                                viewModel = profileVm,
                                onClose = { profileEditOpen = false },
                            )
                        }
                        // OutgoingCallScreen — fullscreen overlay поверх всего кроме Splash.
                        // Рендерится ВНЕ AppScaffold (как Profile, QuotePicker): занимает весь экран
                        // edge-to-edge, скрывает BottomTabs через callContext != null в showBottomTabs.
                        val activeCallCtx = callContext
                        if (activeCallCtx != null) {
                            // На время звонка прячем IME — иначе если до старта звонка EditText
                            // MessagePanel'а из ChatDetail держал фокус, Android может показать клаву
                            // при возврате из background'а (например, после сворачивания приложения).
                            // Hide на mount + ON_RESUME пока overlay активен.
                            DisposableEffect(activeCallCtx) {
                                val controller = WindowInsetsControllerCompat(window, window.decorView)
                                controller.hide(WindowInsetsCompat.Type.ime())
                                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                        controller.hide(WindowInsetsCompat.Type.ime())
                                    }
                                }
                                lifecycle.addObserver(observer)
                                onDispose { lifecycle.removeObserver(observer) }
                            }
                            com.example.template.feature.calls.OutgoingCallScreen(
                                context = activeCallCtx,
                                selfAvatar = container.repository.currentUser.value.avatar,
                                repository = container.repository,
                                onClose = { callContext = null },
                            )
                        }
                      } // /if (mainUiReady)
                        // Splash-оверлей — последний child outer Box'а, рисуется поверх всего
                        // (AppScaffold, ChatDetail, Profile, ProfileEdit). Прячется когда
                        // mainUiReady && прошла min-duration. После fadeOut флипает splashGone
                        // → MainScaffold стартует pre-warm Spaces/Calls (раньше — сразу).
                        SplashOverlay(
                            mainUiReady = mainUiReady,
                            onGone = { splashGone = true },
                        )
                    }
                }
            }
        }
    }
}
