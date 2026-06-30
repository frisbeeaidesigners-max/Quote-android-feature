# android-template

Каркас Android-мессенджера для тестирования дизайн-гипотез. Использует компоненты из `../android-components` через Gradle composite build.

## Главные принципы

- **Источник истины — `android-components`** (сторибук дизайн-системы). Не дублируем компоненты, не меняем их API в нашем репо. Имена ассетов оставляем канонические (`avatar_NN.png`, не переименовываем).
- **Правки в `../android-components` — только с явного согласия пользователя.** Это соседний репозиторий, изменения в нём пользователю труднее отслеживать из контекста android-template. Если задача требует изменения компонента (новый параметр в `configure(...)`, фикс внутреннего поведения, исправление баги в gallery preview и т.п.) — СНАЧАЛА описать предполагаемую правку и спросить согласие, и только потом редактировать файлы в `../android-components`. Локальный фикс/обход в android-template, даже если он более громоздкий, по умолчанию предпочтительнее, чем «тихое» изменение компонентов.
- **После получения согласия — отдельная ветка в `../android-components`.** До любой правки файлов в `../android-components` создать там отдельную feature-ветку от текущего `main` (`git -C ../android-components checkout -b <branch>`). Коммитить и пушить правки только в эту ветку — НЕ в `main`. Имя ветки согласовать с задачей в android-template (например, та же feature-ветка `feature/<topic>`). Никаких прямых коммитов в `main` соседнего репо.
- **Не добавлять несогласованных фич.** Делаем только то, о чём явно попросили.
- **Никаких внутренних enum-имён `DSBrand`** в исходниках android-template. Используем только NATO-кодовые имена: `foxtrot/tango/sierra/kilo/love`. Маппинг кодового имени в enum — через `DSBrand.byCodename(codename)` (определён в android-components). Резолвинг enum-значения происходит внутри библиотеки, наружу оно не утекает.
- **Никаких хардкод-цветов/иконок/шрифтов.** Только `DSColors`, `DSBrand.<x>ColorScheme(isDark)`, `DSIcon.named(...)`, `DSTypography.*`.

## Стек

Kotlin 1.9.22 · AGP 8.2.2 · Compose BOM 2024.02.00 · Compose Compiler 1.5.10 · compileSdk 34 · minSdk 26 · JDK 17 (Android Studio JBR) · kotlinx-serialization · androidx.navigation:compose

## Сборка

```bash
./gradlew :app:assembleDebug                       # foxtrot (default)
./gradlew :app:assembleDebug -Pbrand=tango         # любой из foxtrot/tango/sierra/kilo/love
./gradlew :app:installDebug -Pbrand=sierra
./gradlew :core:data:testDebugUnitTest
```

Требуются соседние клоны: `../android-components`, `../icons-library`.

## Структура модулей (NiA-стиль)

```
:app                              — entrypoint, NavHost, AppContainer (DI)
:core:model                       — data-классы (kotlin-jvm, без android)
:core:data                        — MessengerRepository, MockRepositoryImpl, JSON mock
:core:ui                          — AppTheme, Host-адаптеры, custom Compose
:core:navigation                  — NavRoute (Main→ChatDetail), TabRoute (legacy, табы не на NavHost), extensions
:feature:{chats,spaces,calls,profile,chatdetail}
build-logic/                      — convention plugins
```

Правила зависимостей:
- `:feature:*` не зависит от других `:feature:*` (только через `:core:navigation`-routes)
- `:core:model` без android-зависимостей
- `:core:ui` использует `:components` через composite build

## Composite build с android-components

`settings.gradle.kts`:
```kotlin
includeBuild("../android-components") {
    dependencySubstitution {
        substitute(module("com.example:components")).using(project(":components"))
    }
}
```

## Build-time бренд

`-Pbrand=<codename>` → `BuildConfig.BRAND_CODENAME` (объявлен в `:app` и `:core:ui`) → `AppBrand: DSBrand = DSBrand.byCodename(...)` → `LocalAppBrand` в Compose. Все компоненты получают `brand` через CompositionLocal и передают в `<X>ColorScheme.from(brand, isDark)`.

## Синк ассетов (auto-preBuild)

`app/build.gradle.kts` имеет Copy-task'и, привязанные к `preBuild`:
- `syncIconsFromLibrary` — `../icons-library/icons/*.svg` → `app/src/main/assets/icons/`
- `syncIconsLocal` — `app/src/main/assets-local/icons/*.svg` → `app/src/main/assets/icons/` (mustRunAfter syncIconsFromLibrary; локальные SVG, которых ещё нет в icons-library; git-tracked, в отличие от `assets/icons/`). При совпадении имён локальная версия перекрывает библиотечную. Когда иконка попадёт в библиотеку — удалить из `assets-local/icons/`, код менять не надо. См. `app/src/main/assets-local/icons/README.md`.
- `syncImagesFromComponents` — `../android-components/app/src/main/assets/images/*.{png,jpg}` → `app/src/main/assets/images/`
- `syncPatternsFromComponents` — `../android-components/app/src/main/assets/patterns/*.svg` → `app/src/main/assets/patterns/`
- `syncStickersFromComponents` — `../android-components/app/src/main/assets/stickers/**/*.png` → `app/src/main/assets/stickers/` (с подпапками pack1/pack2)
- `syncGifsFromComponents` — `../android-components/app/src/main/assets/gifs/*.{webp,gif}` → `app/src/main/assets/gifs/`

Целевая `app/src/main/assets/icons/` (и аналогично images/patterns/stickers/gifs) в `.gitignore` — генерируется на сборке. Имена ассетов сохраняем канонические.

**Важно:** без этого синка `DSIcon.named(context, "name", ...)` возвращает `null` (читает `icons/<name>.svg` из app-assets).

## Данные

JSON-mock в `app/src/main/assets/mock/`:
- `current-user.json` — `CurrentUser`
- `users.json` — список `User` (имеет `personaId`)
- `personas.json` — справочник людей (имя, фамилия, `avatarAsset`, `gradientIndex` для accent-цвета)
- `spaces.json`, `chats.json`, `calls.json`
- `messages/<chatId>.json` — лениво при открытии чата; отсутствие файла = пустой список (защита в `MockRepositoryImpl.loadMessages`)

Полиморфизм `Message` через `classDiscriminator = "type"` в `JsonModule.AppJson`.

## Использование компонентов

### Аватарки
- `Person` с `Persona.avatarAsset` → `AvatarView.IMAGE`; без — `AvatarView.INITIALS` с `gradientIndex` из persona.
- `Self` (Сохранёнка) → `AvatarView.SAVED`. Боты → `AvatarView.BOT`. Группы/каналы — INITIALS из `chat.avatar`.
- Bitmap'ы прогреваются в daemon-треде из `AppContainer`, доступ через `LocalBitmapCache`. Observable `BitmapCache.version: State<Int>` бампается при каждой новой записи — потребители читают `.value` в @Composable scope чтобы recompose'иться при докинутой аватарке. **В LazyColumn version читается ОТДЕЛЬНО в каждом item-scope** (lazy items пропускают рекомпозицию при стабильных inputs).

### ChatListItem (Compose)
- `:components/chatlist/ChatListItem.kt` — pure Compose, ChatListHost зовёт напрямую. XML-вариант `ChatListItemView.kt` оставлен в components для gallery preview.
- `showDivider: Boolean` контролирует разделитель (не хак с обнулённым `dividerColor`).
- `senderAvatar: Bitmap?` для Group-превью — через `MessagePreview.senderPersonaId` → `repository.getPersona(id).avatarAsset`.

### Выравнивание и фон по превью из android-components
Если в gallery компонент выровнен/спозиционирован определённым образом — соблюдаем то же у себя (например `BottomTabs` — pill центрирован, без бэкграунда у контейнера → `Scaffold.containerColor = Color.Transparent`).

### AndroidView-обёртки (правило для всех Host'ов)
Любая компонента из `:components`, рассчитанная на полную ширину контейнера (Headers, MessagePanel, ChatListItemView, message-бабблы) должна оборачиваться так:
```kotlin
AndroidView(
    modifier = Modifier.fillMaxWidth(),
    factory = { ctx ->
        SomeView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
    },
    update = { ... },
)
```
Иначе View получает WRAP_CONTENT по горизонтали, и стили (gravity, выравнивание My/Someone у бабблов, ширина search bar) работают неправильно.

### HeaderConfig
- `Main.Mode` есть только `CHATS` и `SPACES`. Calls использует `Main(mode=CHATS, onPlusClick=null)` — null скрывает плюс-кнопку (правка в `:components/HeadersView`: `onPlusClick: (() -> Unit)? = {}`, согласована). Для Profile — `HeaderConfig.Custom`.
- `Chat`-конфиг — один на P2P/Group/Channel, различие в `subtitle` и `showCallButton`.
- `subtitleStyle=TYPING` гейтится `if (!c.subtitle.isNullOrEmpty())` — при TYPING нужно передать осмысленный текст («печатает»), иначе subtitle не отрисуется.

### Bubbles (MessageList)
- `:core:ui/hosts/MessageList.kt` гонит `Message.Text/Media/Voice/Link/CallMeet` в соответствующий AndroidView из `:components/bubbles` (BubblesView, MediaBubbleView, VoiceBubbleView, LinkBubbleView, CallMeetView).
- `LazyColumn(reverseLayout = true)` — последнее сообщение прилипает к низу, листать вверх → старые. Данные oldest→latest переворачиваются через `messages.asReversed()` (cached в remember).
- **Same-sender grouping**: `computeGroupPositions` считает SINGLE/FIRST/MIDDLE/LAST по `(senderId, isMine, timestamp)` с порогом 5 минут. Управляет padding'ом, углами бабла (per-corner radii), и для SOMEONE — sender name на первом + avatar на последнем.
- **КОНВЕНЦИЯ P2P sender name**: в P2P-чатах sender name над SOMEONE-баблами НЕ показываем (собеседник один). Реализация — `MessageList(isP2P)` передаёт `sender = ""`, все 5 баббл-компонентов гейтят пустой sender через `sender.isNotEmpty()` в `setupSomeoneBubble`. **При добавлении нового баббл-компонента — повторить эту проверку**, иначе правило сломается.
- **Системные сообщения (Joined, MessageDeleted, date-разделители)**: через `SystemMessageView` из `:components/systemmessage`, унифицированы в `RowItem.SystemRow`. Текст рендерится в `MessageRows.renderSystemText` по `SystemKind` (для `MessageDeleted` — «Вы удалили сообщение» / «Автор удалил(а) сообщение»). `RowItem.SystemRow.messageId` хранит id оригинального `Message.System` (null для date-разделителей) — нужен для подсветки row'а при тапе на reply-block (basic10 pulse на всю ширину, 300+300мс). Group-spacing 4dp/8dp в `computeTopPadding`. Sticky-date overlay показан ТОЛЬКО во время скролла (`fadeIn(150ms)` когда inline DateRow вне viewport, `fadeOut` через 1.4s после остановки). Формат: Сегодня / Вчера / «18 мая» / «18 мая 2025».
- **sendingState**: маппится через `MessageStatus.toBubblesState/toMediaState/toVoiceState/toLinkState` (каждый bubble имеет nested enum). CallMeet — `status` это статус звонка (ANSWERED/REJECTED/MISSED/NO_ANSWER), не отправки.
- **Voice playback (mock)**: VM держит `voicePlayback: StateFlow<VoicePlayback?>` (см. `core/ui/hosts/VoicePlayback.kt` — `messageId/isPaused/position 0..1`). Один баббл активен на чат, тап на другой сбрасывает текущий. Тикер в `viewModelScope.launch` инкрементирует position раз в 50мс относительно `SystemClock.elapsedRealtime()` anchor'а (чистая функция от времени, без drift'а). На position≥1 → state `null` (баббл в DOWNLOADED). MessageList мапит state в `VoiceBubbleView.PlaybackState` (DOWNLOADED как resting — реального download'а нет, аудиофайла тоже нет), подписывает `onPlayPauseClick` → `viewModel.toggleVoicePlayback(id)` и `onSeek` → `viewModel.seekVoicePlayback(id, pos)` (на ACTION_UP драга по waveform; рестартит тикер с новой позиции, сохраняя play/pause-state). `remainingTime` пересчитывается в update лямбде. Реального аудио нет — это визуальный mock.
- **Voice swipe-reply / context menu**: Voice включён в `SwipeToReplyItem.enabled` (наравне с Text/Media). `MessageList` для Voice добавляет `OnLayoutChangeListener` так же, как для Bubbles/MediaBubble — измеряет `bubbleContent.right` для позиционирования reply-индикатора. `ChatDetailViewModel.openContextMenu` мапит Voice на `MessageKind.TEXT` (EDIT/COPY в меню остаются, но click no-op: `startEdit` early-return для Voice, `copyTextFor(Voice)=""` гейтит запись в clipboard).
- **Reply-block bg fill**: `Bubbles/Media/VoiceColorScheme` несут поля `replyBackgroundColor` + `replyBackgroundRadiusDp` (data-class defaults TRANSPARENT/0 для нейтральных потребителей). `DSBrand.{bubbles,media,voiceBubble}ColorScheme(isDark)` подкручивают эти поля в `basicColor06` + `6dp` — android-template поэтому НЕ оверрайдит схему в `MessageList.kt`. На стороне `:components` `*BubbleView` держит `replySectionBackground: GradientDrawable`, привязывает в init, мутирует `setColor` + `cornerRadius` в configure рядом с `replyBorderBackground`. `replySection.clipToOutline = true` чтобы 2dp border-bar следовал rounded outline'у. Вертикальный inset — margins'ы на content + topMargin иконки quote (не setPadding на секции — иначе MATCH_PARENT bar обрезался бы). Горизонтальный inset 12dp: в Text/Voice идёт через `bubbleContent.setPadding`, в `MediaBubbleView` — через `marginStart/marginEnd` на LayoutParams replySection (своего padding'а у секции нет, медиа остаётся edge-to-edge).

### MessagePanel (MessagePanelHost)
- Полная brand-настройка: `attachedMediaColorScheme`, `segmentedControlColorScheme`, `brandAccentColor`, `stickerAddButtonColorScheme` (без них на не-foxtrot брендах вылезают зелёные пятна — дефолты внутри компонента).
- Контент пикеров — из synced assets: `stickers/pack1/` (15 PNG), `stickers/pack2/` (14 PNG), `gifs/` (9 webp).
- 5 демо-вложений (`media_*.jpg`) — state в хосте. `onAttachClick` при пустом списке восстанавливает дефолт; раскрытие секции делает сам компонент через `pendingExpandAttachments`.
- **Voice mode** state в хосте: `onMicClick/onSendVoice/onDeleteVoice`, `panel.startVoiceRecording()/stopVoiceRecording()` через update lambda. На `onMicClick` дёрнём `panel.dismissKeyboard()` — input скрыт в voice-режиме, IME оставлять открытой нет смысла.
- **Voice send (без записи аудио)**: длительность считаем у себя — захват `SystemClock.elapsedRealtime()` в `onMicClick`, разница в `onSendVoice`. Та же монотонная шкала, что у внутреннего `voiceTickRunnable` панели → значение совпадает с таймером в момент Send. Хост шлёт `onSendVoice(durationMs)` наружу → `ChatDetailViewModel.sendVoice(durationMs)` → `repository.sendVoiceMessage(chatId, durationMs, replyTo)`. `MockRepositoryImpl.buildSentVoiceMessage` синтезирует waveform: `synthesizeWaveform(durationMs, seed=timestamp)` — ~20 баров/сек, диапазон 10..90, детерминированный seed.
- **Voice-mode home-indicator accent**: при `voiceMode=true` MessagePanelHost эмитит `onVoiceModeChange(true)` СИНХРОННО из click handler (не через LaunchedEffect — был бы 1-кадровый лаг). `MainActivity` хранит `panelVoiceMode`, рисует Box высотой `WindowInsets.navigationBars` поверх AppScaffold с `brand.accentColor(isDark)` — это полоса под home-индикатором. На back с активным voice-режимом полоса уезжает вправо синхронно со страницей переписки — для этого `animProgress` (Animatable, драйвящий slide-out ChatDetail) поднят на root scope MainActivity, и полоса применяет тот же `translationX = screenWidthPx * (1f - animProgress.value)`. `DisposableEffect.onDispose` MessagePanelHost дополнительно гасит флаг при unmount.
- **Reply во время voice-mode**: компонент (`MessagePanelView.startVoiceRecording`) сохраняет `contextLayer` видимым, если он был раскрыт ДО входа в voice (правка в `../android-components`, согласована). IME при появлении нового reply гейтится `isVoiceMode` в `ChatDetailScreen` (локально трекаемый через перехват `onVoiceModeChange`) — `LaunchedEffect(replyCtx?.originalId)` не поднимает клавиатуру в voice-режиме.
- **Send semantics**: attachments → `onSendMedia(..., caption = text)`, иначе → `onSendText(text)`. Voice → `onSendVoice(durationMs)`. После отправки — `panel.clear()`. Reply-context из VM прикрепляется ко ВСЕМ типам.
- **Тап вне панели**: `ChatDetailScreen` оборачивает Column в `pointerInput { detectTapGestures { panelRef.dismissKeyboard() } }`. `panelRef` приходит через `onPanelReady` callback. Тапы внутри панели consume'ятся раньше — outer не срабатывает.
- **Per-section top divider (правка в `:components/messagepanel/MessagePanelView.kt`)**: глобальный 1dp `divider` сверху `contentLayout` удалён — в voice-режиме alpha-blend через accent root bg давал тинтнутую линию. Каждая секция (Reply `contextLayer` + default `mainPanel`) рисует свой top-stripe через кастомный `Drawable` helper `topBorderedBackground(borderColor, fillColor)`: сначала opaque `fillColor` целиком, потом alpha-`borderColor` сверху 1dp → blend идёт против свежего fillColor, не против root accent. `voicePanel` без своей stripe — в voice-режиме перекрывает stripe `mainPanel`'а. **Top-divider всегда ОДИН**: при `applyContextBlock` open — border снимается с `mainPanel` ДО expand-анимации, при close — возвращается ПОСЛЕ полного collapse'а (через `refreshMainPanelBackground()`).

### BottomTabs
- `BottomTabsView` в `:components` — только data-holder (enum Tab + TabBadgeType + поля XML-атрибутов).
- Рендеринг — `@Composable fun BottomTabs(...)` из `com.example.components.bottomtabs.BottomTabs`. Public API, принимает `selectedTab`, `onTabSelected`, `avatarBitmap` (для profile-таба), цветовые схемы.
- В `:core:ui/hosts/BottomTabsHost.kt` — обёртка с index-API: `selectedTabIndex: Int` + `onTabSelected: (Int) -> Unit`. Маппинг индекс ↔ `BottomTabsView.Tab` владеет хост через `TABS_ORDER`. Не зависит от NavController.
- **Бейджи**: `badgeCounts: List<Int>(4)` — число чатов, которые удовлетворяют `mention || (!muted && unreadCount>0)`. Mention пробивает mute (упоминание явное и должно быть видно даже в замьюченном чате). Считается в `MainScaffold` по `repo.p2pChats` (для Чатов) и `repo.allSpaceChats` (для Пространств). Не общее число сообщений — именно число чатов.

### Табы и переходы
Tab-навигация **не на NavHost** — он пересоздавал композицию destination'а на каждом переходе, для тяжёлых экранов (ChatListHost) это давало заметные лаги. В `MainScaffold`: `currentTab: Int` (saveable), `mountedTabs: Set<Int>`, `animatedTab` через `animateFloatAsState(220ms, LinearOutSlowInEasing)`. Все смонтированные экраны рендерятся параллельно в `BoxWithConstraints` с `Modifier.graphicsLayer { translationX = (index - animatedTab) * widthPx }`. **Lazy mount + pre-warm**: на старте смонтирован только Chats, остальные подгружаются в фоне через `LaunchedEffect` с шагом 600мс. После прогрева переключение — чистый GPU-translate. ChatDetail — отдельный state-driven overlay (см. «Производительность»), не таб.

### Кнопки (ButtonHost)
- `:components/button/ButtonView` — все pill/icon-кнопки. Размеры L/M/S/XS (56/48/40/36dp), filled/unfilled, ripple = content-color × 10% alpha, corner-radius pill (1000dp), padding'и по размеру. Цветовые схемы — `brand.buttonColorScheme(ButtonType, isDark)` (PRIMARY = accent, SECONDARY = basicColor55 unfilled / basicColor08 filled, SYSTEM = danger).
- Compose-обёртка: `core/ui/hosts/ButtonHost.kt`. Параметры зеркалят `ButtonView.configure`. Использовать ВЕЗДЕ вместо самописных `Box + .clickable {}` — иначе ripple/padding/tint придётся подгонять руками и они всё равно не совпадут с библиотечными.
- Кастомизация фона/контента — `brand.buttonColorScheme(...).copy(filledBackground = ..., filledContentColor = ...)`. Не собирать `ButtonColorScheme` с нуля.

### Инпуты (InputField)
- `:components/input/InputField` — публичный Compose-компонент. Все текстовые поля проекта рендерятся им (плавающий label, focus-line, встроенный clear-icon `close-s`, multiline, suffix, disabled/error states). Не катать `BasicTextField + Box + Row` руками.
- Color scheme: `brand.inputColorScheme(isDark)` — bg `basicColor04`, label `basicColor40`, text `basicColor90`, hint `basicColor50`, disabled `basicColor25`.
- API: `InputField(value, onValueChange, label, colorScheme, isEnabled, isError, isMultiline, lines, hint, hintPosition, suffix, keyboardType, onFocusLost)`. State снаружи (stateless), хранить через `var x by remember(key) { mutableStateOf(...) }`.

### Тапабельные не-`:components` элементы (appClickable)
- `core/ui/AppClickable.kt`: `Modifier.appClickable(onClick, enabled = true)` — composed-обёртка над `clickable` с Material1 `rememberRipple(color = brand.basicColor55(isDark))`. Тот же фидбэк, что у `ButtonView` SECONDARY.
- **Дефолт для собственных строк/плиток/чипов**, которых нет в `:components/`. Не использовать сырой `Modifier.clickable(onClick = ...)` — Material3 ripple серый по-умолчанию, не совпадает с библиотекой. Не катать руками `MutableInteractionSource + rememberRipple` — уже есть в `appClickable`.
- Для библиотечных компонентов (`ButtonView`, `ChatListItemView` и т. д.) ripple идёт изнутри, `appClickable` там не нужен.

### Профиль (overlay, не таб)
- `feature/profile/ProfileScreen` + `ProfileEditScreen` — два fullscreen-оверлея в `MainActivity`, рендерятся ВНЕ `AppScaffold` (как `QuotePickerFullScreen`). Без slide-анимации, появляются/исчезают мгновенно. Каждый сам делает `.fillMaxSize().background(brand.backgroundBase).statusBarsPadding().navigationBarsPadding()`.
- Открытие: BottomTabs tap по index 3 (`TAB_COUNT - 1`) НЕ переключает `currentTab`, а зовёт `onProfileClick()` → `profileOpen = true`. `MainScaffold` пропускает рендер index 3 как tab и не пре-warm'ит его.
- `showBottomTabs = openChatId == null && !profileOpen`. Edit-страница открывается тапом Edit в хедере Profile → `profileEditOpen = true` (отдельный state поверх profileOpen).
- Тап вне инпута в Edit-форме — `LocalFocusManager.current.clearFocus()` через `pointerInput { detectTapGestures }` на корневом Column → IME уезжает. `detectTapGestures` фильтрует unconsumed-тапы, ButtonView/InputField забирают свои клики сами.
- `CurrentUser` (`core/model`) расширен: `firstName/middleName/lastName` (опционально), плюс существующее `name`. `personas.json[pe-self]` — `Анна Иванова / Female`. `current-user.json` — `Анна Александровна Иванова / PR-менеджер`.

## Производительность

- **Tab-переходы** — кастомный offset-layout с lazy mount и фоновым pre-warm всех табов после первой композиции Chats (см. «Табы и переходы»). NavHost для табов не используется. Анимация — pure GPU translate (`Modifier.graphicsLayer { translationX = ... }`, НЕ `Modifier.offset { ... }` — это давало layout-pass на тяжёлые AndroidView каждый кадр), без рекомпозиции тяжёлых хостов.
- **ChatDetail — state-driven overlay, не NavHost.** В `MainActivity` хранится `openChatId: String?` (`rememberSaveable`); MainScaffold отрендерен всегда, ChatDetail рисуется поверх в `Box` когда `openChatId != null`. Системный back — через `BackHandler`. При выходе закрываем IME явно через `WindowInsetsControllerCompat.hide(Type.ime())` — без этого клавиатура «висит» на чат-листе, потому что Compose ничего не диспозит и фокус сохраняется.
- **Slide-in + parallax анимация ChatDetail.** В `MainActivity`: единый `Animatable(0f)` + два `graphicsLayer { translationX = ... }` (фон уезжает влево на 20% ширины, ChatDetail заезжает справа от +fullWidth до 0). `tween(280, FastOutSlowInEasing)`. Не `AnimatedVisibility` — она умеет анимировать только свой контент, а нужно синхронно двигать и нижний слой. `lastChatVm` через `remember` + `LaunchedEffect(chatVm)` удерживает VM на exit-фазе (внутри content используем `chatVm ?: lastChatVm.value`). **Подробный разбор грабель (read animProgress.value только в draw scope, mount-гейт через `chatVm != null || keepMountedForExit` синхронный) — в комментариях `MainActivity.kt`.**
- **Cold-open chat: ~280мс intrinsic Compose cost** — bottleneck в композиции MessageList + MessagePanelView. Замеры/планы оптимизации (вплоть до Compose-rewrite bubbles) — в `UXtweaks.md`. Утилита замеров — `core/ui/.../Timing.kt` (`reset()`/`mark(tag)` → `adb logcat -s TIMING:D`).
- **`MockRepositoryImpl.loadMessages` парсит JSON через `withContext(Dispatchers.IO)`** — viewModelScope.launch по умолчанию `Main.immediate`, без обёртки парсинг блокировал UI на cold-open. Кэш-hit остаётся синхронным.
- **`MainScaffold(showBottomTabs)`**: когда ChatDetail активен — BottomTabs не композируются. Их `.clickable` под центром экрана иначе конкурирует с EditText'ом MessagePanel за тапы.
- **`HeaderHost`**: `configure()` вызывается через `DisposableEffect(view, config, colorScheme)`, а не из `AndroidView.update`. Иначе update срабатывает каждый кадр и `HeadersView.configure` через `removeAllViews()` пересоздаёт кнопки header'а — тап теряется между ACTION_DOWN и ACTION_UP.
- **`ChatListHost` на Compose**: `ChatListItem` (Compose) вместо AndroidView над XML. AvatarColorScheme precomputed через `remember(brand, isDark)`, время формируется через `remember(chat.id, timestamp)`, bitmap'ы из shared `LocalBitmapCache`.
- **IME-педантика**: `windowSoftInputMode = adjustResize` в манифесте + `Modifier.imePadding()` ТОЛЬКО на `MessagePanelHost`, не на Column'е ChatDetail (иначе окно сокращается на высоту IME во время анимации и снизу проглядывает MainScaffold). На корневом Column есть `background(brand.messageScreenBackground(isDark))` — страховка от кадров когда BackgroundPatternView отстаёт с remeasure.
- **`AndroidView`-обёртки** — основное узкое место. Если перформанс критичен — диф-чек в `configure(...)` или Compose-rewrite (как с ChatListItem).
- **configKey-diff для бабблов Text/Media/Voice** в `MessageList.kt`: каждая ветка собирает снапшот «структурных» inputs configure'а в `List<Any?>` и хранит `lastKeyState = remember(msg.id)`. На каждом `update` снимок сравнивается с предыдущим — если не изменился, `view.configure(...)` пропускается. Skip'ает переинициализацию drawables / swap иконок / расчёт group-corner radii когда рекомпозиция вызвана cacheVersion bump'ом, swipe-state'ом соседних items или другой «не структурной» инвалидацией. **В configKey обязательно: `selectionActive` (иначе onReplyBlockClick лямбда не перезавелась бы при toggle multi-select) и `msg.replyTo?.quoteStart != null`.** У Voice ветки — дополнительно `pbState`, `voicePlayback.position` НЕ в ключе (на тиках идёт lightweight `view.updatePlaybackPosition(...)`).

## Поведение отправки

- Новое сообщение публикуется сразу как `DELIVERED` (без промежуточного `SENDING`).
- Переход в `READ` через ~800мс делается ТОЛЬКО если:
  - **P2P** — собеседник (`participantIds[0]`) в `UserStatus.Online`. Иначе остаётся `DELIVERED` пока «человек не появится».
  - **Group/Channel** — всегда (имитируем «кто-то прочитал»).
- Test coverage: `core/data/src/test/.../SendMessageTest.kt` — проверяет `StatusTransitions.sequence` и `intervalsMs`.

## Bootstrapping и UX-мелочи

- **Пустые чаты**: если `mock/messages/<chatId>.json` отсутствует и `chat.lastMessage` — входящий Text, `MockRepositoryImpl.loadMessages` синтезирует один `Message.Text` от первого участника с текстом/timestamp превью. Защита от дубля — `messages.any { it.timestamp == preview.timestamp }`.
- **Sequential reveal бейджей BottomTabs**: при первом запуске `revealedBadges` инкрементируется каскадом (`LaunchedEffect` + `withFrameNanos`, шаг 180мс `BADGE_REVEAL_STAGGER_MS`). Используется существующая spring pop-in анимация на переходе count 0→N.

## Известные gotcha

- **`TabRoute.companion.all`** должен быть `by lazy { listOf(Chats, ...) }` — без `lazy` nested `data object`'ы могут быть `null` при инициализации companion (JVM class-loading order). Сейчас `TabRoute` фактически не используется (табы перешли на offset-layout), но оставлен для возможного возврата к NavHost.
- **`HeaderConfig.Chat`** — не `P2P`/`Group`/`Channel` отдельные классы (как утверждает README components), а единый `Chat` со `subtitle`.
- **CRLF warnings** на Windows при коммитах — игнорируем, не влияют на сборку.
- **Windows: не цепляй `gradlew && adb install` одной строкой**. Gradle асинхронно дописывает APK, `adb install -r` параллельно зачитывает СТАРЫЙ APK с диска и отчитывается `Success`, а параллельно `mergeDexRelease` падает с locked `classes.dex`. Запускай шагами: сначала `assembleRelease`, дождись `BUILD SUCCESSFUL`, потом `adb install`. Если `mergeDexRelease` упал — `rm -rf app/build/intermediates/dex/release` и retry.
- **`@Transient val thumbnail: Any?`** в `MediaAttachment` (`core.model`) хранится как `Any?`, не `Bitmap`, чтобы `:core:model` оставался pure-Kotlin без android-зависимостей. Потребитель в `:core:ui` кастит обратно через `as? Bitmap`.
- **`RobotoFamily` из `:components/DSTypographyCompose.kt` объявляет только Normal/Medium/Bold** (до 700). При `FontWeight.Black` (900) поверх `DSTypography.*.toComposeTextStyle()` Compose не находит 900-глиф и рисует Bold с faux-bold синтезом — визуально неотличимо от 700. Если нужна настоящая 900 (см. `SplashOverlay.kt`) — override'нуть `fontFamily=FontFamily.Default` (системный Roboto содержит Roboto-Black). Добавлять новый weight в `RobotoFamily` — правка в `:components` (требует согласия).

## Что вне scope текущей версии

- Реальный backend и БД (прототип чисто визуальный)
- Создание чатов/групп/каналов
- Запись реального аудио для voice (отправка — реализована mock'ом по таймеру панели; waveform синтетический; реального playback'а тоже нет — только визуальный mock)
- Поиск (UI бара показывается, поведение overlay не реализовано)
- Login/onboarding/settings экраны
- Edit на Voice/Link/CallMeet (в меню для Voice EDIT появляется, но click no-op'нет)
- Reply/Edit на Link/CallMeet (контекстное меню для них не открывается — см. `ChatDetailViewModel.openContextMenu`)

Реализовано:
- Отправка Text + Media (с подписью или без; demo-вложения из синканных `media_*.jpg`)
- Отправка Voice — длительность считается на хосте через `SystemClock.elapsedRealtime()` (совпадает с таймером панели в момент Send), waveform синтетический (`MockRepositoryImpl.synthesizeWaveform`)
- Mock-playback voice — play/pause/seek через `VoicePlayback` state в VM, waveform-позиция тикает по wall-clock; auto-stop на конце; один баббл активен в раз (см. Bubbles секция выше)
- Voice swipe-reply (reply-индикатор позиционируется через `OnLayoutChangeListener` на `VoiceBubbleView` так же, как для Bubbles/MediaBubble) и context-menu (через MessageKind.TEXT)
- Voice-mode home-indicator accent под home-индикатором (см. MessagePanel секция)
- Контекстное меню (`ContextMenuView` Compose-overlay в `MainActivity` вне `AppScaffold`): Copy / Delete / Reply / Edit
- Реакции под бабблом — toggle через меню или тап по существующей реакции
- Reply на Text/Media/Voice (`ReplyPreview`-snapshot переживает удаление оригинала) + swipe-влево по баблу (`SwipeToReplyItem` в `:core:ui/hosts/SwipeToReply.kt`)
- Edit на MY Text/Media (`EditContext` mutual-exclusive с reply, Media→Text конверсия при empty attachments)
- Selection mode (long-press → multi-select, SelectionActionBar по Figma)
- Quote-reply: V1 in-place (long-press по баблу → floating Copy/Цитировать, multi-select) + Fullscreen picker (`feature/chatdetail/quotepicker/QuotePickerFullScreen.kt` + `QuoteFullScreenContent.kt`; рендерится inline overlay'ем в `MainActivity` вне `AppScaffold`). Тап на reply-block в чужом баббле скроллит к оригиналу с bubble-pulse + (если есть quote) fragment-overlay'ем; при `MatchResult.QuoteMismatch` — pulse + toast «Цитата не найдена»; повторные тапы блокируются гейтом `replyTapInProgress` на 2100мс. **`BackHandler` живёт ВНУТРИ `QuoteFullScreenContent`, не в `QuotePickerFullScreen`** — нужен доступ к `clearSelectionRef`, чтобы синхронно скрыть selection-handle-popup'ы custom-controller'а до unmount'а picker'а (иначе остаются «подвешенные» окна). **IME-hide для picker'а** (`MainActivity.kt`): пока `quotePickerVisible == true`, в дополнение к `controller.hide(ime())` на open временно переключаем `window.softInputMode` STATE-биты в `SOFT_INPUT_STATE_ALWAYS_HIDDEN` (сохраняя ADJUST_RESIZE через `SOFT_INPUT_MASK_ADJUST`). На resume window-focus event иначе спонтанно поднимает IME для focused EditText'а (MessagePanel в underlying ChatDetail или SelectableEditText в preview), даже если на open мы её спрятали. ON_RESUME `LifecycleObserver` оставлен как belt-and-suspenders для ROM'ов, игнорирующих ALWAYS_HIDDEN. `originalSoftInputMode` восстанавливается в `onDispose` **до** `controller.show(ime())` — иначе ALWAYS_HIDDEN заблокировал бы restore. Pattern параллельный `callContext`-IME-hide.
- 3-стиль Quote-picker через SegmentedControl в Profile + ortho «Рендер ссылок» switch:
  - `QuotePickerStyle.FULLSCREEN` — inline overlay в Activity-окне; `linkRender` гейтит
    V4 (без internal segmented) vs V5 (с «Ответ/Ссылка» SegmentedControl и LinkBubble tab'ом)
  - `QuotePickerStyle.MODAL_DOTS` — Compose Dialog поверх ChatDetail; dots+swipe между
    «Ответ»/«Ссылка»; `linkRender=false` скрывает chrome → static Title/Description footer
  - `QuotePickerStyle.MODAL_BUTTONS` — Compose Dialog; arrow-buttons по бокам Title/Description;
    swipe отключён, переключение тапом; `linkRender=false` → static footer
  - State в AppContainer: `quotePickerStyle: MutableStateFlow<QuotePickerStyle>` (default
    `FULLSCREEN`) + `linkRenderEnabled: MutableStateFlow<Boolean>` (default `true`)
  - 2 CompositionLocal'а: `LocalQuotePickerStyle` + `LocalLinkRenderEnabled` (см.
    `core/ui/.../QuotePickerVariant.kt` — файл хранит оба)
  - Dispatcher в MainActivity: `when (style) { FULLSCREEN -> QuotePickerFullScreen(linkRenderEnabled =
    linkRender, ...); MODAL_* -> QuotePickerModalHost(style, linkRender, ...) }`
  - IME-hide override (softInputMode + Lifecycle ON_RESUME) + tap-barrier Box применяются
    ТОЛЬКО для FULLSCREEN; Modal Dialog имеет FLAG_ALT_FOCUSABLE_IM и своё Window
- Удаление сообщения → `Message.System(MessageDeleted)`-placeholder вместо filter'а в `MockRepositoryImpl.deleteMessage`. Текст в `MessageRows.renderSystemText`: «Вы удалили сообщение» (isMine) / «Автор удалил(а) сообщение». Reply-блок в баббле, цитирующем удалённое, показывает «Удалил(а) сообщение» (см. `MessageList.resolveReplyText`). Тап по такому reply-block скроллит к system placeholder с пульсацией row'а (без toast'а)
- Voice в чат-листе превью — `MockRepositoryImpl.updateChatLastMessage(Message.Voice)` шлёт `text = "Голосовое сообщение"` + `ownStatus = msg.status` (own-status тики delivered/read в чат-листе работают как у Text/Media). Аналогично `previewFromLast` для delete-плейсхолдера.
- CallMeet превью в чат-листе — `MockRepositoryImpl.callMeetPreviewText`: «Исходящий/Входящий звонок» по `isMine` либо «Пропущенный звонок» при `callStatus=Missed/NoAnswer`. `buildCallMeetMessage`/`buildOutgoingCallRecord` ставят `Missed` когда `durationMs==0` (положили трубку до connect'а), иначе `Answered`/`Outgoing`.
- Voice reply-target в picker'е — `QuoteBubblePreview` рендерит `VoiceBubbleView` в статике (DOWNLOADED, без playback). FSM `QuoteFullScreenContent` корректно ловит INITIAL_MINIMAL для Voice (нет селекшена текста), заголовок «Ответ на сообщение».
- Voice reply-block с quote-icon + tap-to-jump — `VoiceBubbleView` имеет API `onReplyBlockClick` / `showQuoteIcon` / `quoteIconTint` (правка в `:components`, согласована и закоммичена в `:components`). MessageList Voice-ветка прокидывает их с multi-select-aware лямбдой (тап чистит selection в multi-select или скроллит к оригиналу).
- AudioPanel плавное появление/скрытие — `MainAudioPanelSlot`/`ChatAudioPanelSlot` обёрнуты в `AnimatedVisibility` с `expandVertically + fadeIn` (220мс FastOutSlowInEasing + 150мс) на enter и симметрично на exit. Удерживаем `lastPlayback` (mutableStateOf) на exit-фазе — после null StateFlow данные не поставляет.
- Waveform для коротких voice (`<3с`) заполняет всю waveform-area — `synthesizeWaveform` минимум баров поднят с 12 до 60 (60×4dp = ~240dp покрытия, хватает для типичной ширины voice-bubble).
- Раздел Профиль (4-я вкладка) — fullscreen-оверлей `ProfileScreen` поверх MainScaffold (не tab-контент), BottomTabs скрываются; в хедере кнопки X/Edit. По тапу Edit — `ProfileEditScreen` с формой редактирования (Имя/Фамилия/Отчество/Должность + Email + disabled Phone). Все кнопки через `ButtonHost` (обёртка над `ButtonView`), все поля — `InputField` из `:components/input/`. Save/Back — no-op'нут (закрывают форму).
- Раздел Звонки (3-я вкладка) — `feature/calls/CallsScreen.kt`. Header `Main(mode=CHATS, onPlusClick=null)`. Action-tile'ы («Новая встреча» / «Создать ссылку», `CallActionTile.kt`) **закреплены ВНЕ `LazyColumn`** — при скролле остаются на месте. Список с `stickyHeader` для дат-сепараторов (фон под sticky — `brand.backgroundBase` обязателен, иначе через basic50-текст просвечивают строки). Группировка — `CallsViewModel.sections: StateFlow<List<DaySection>>`. Строки `CallRow.kt`: avatar 40dp без badge'ей, имя `dangerDefault()` при Missed, иконка типа звонка (incoming/outgoing/end-call-filled), `ButtonHost` XS вызова справа. Все onClick — no-op (статика).
- Launcher-иконка — adaptive icon из `C:/testing/logo/logo.svg`. `res/drawable/ic_launcher_foreground.xml` — Vector Drawable из 4-х path'ов исходного SVG (viewport 400 один-в-один, path 2 имеет внутренний контур → `fillType=evenOdd`). `res/values/colors.xml` → `ic_launcher_background=#1A1A1A` (фикс на все бренды). `res/mipmap-anydpi-v26/ic_launcher{,_round}.xml` — adaptive-icon обёртка. Manifest — `android:icon`/`android:roundIcon`. Pre-API 26 PNG'и не нужны (`minSdk=26`).
- Splash launch-screen — `app/SplashOverlay.kt`. `themes.xml.windowBackground=@color/splash_background` (`#1A1A1A`) убирает системный белый splash до первого кадра Compose. Оверлей — full-screen Box с тем же фоном и капитализированным `BuildConfig.BRAND_CODENAME` по центру. Шрифт — `FontFamily.Default` + `FontWeight.Black` (системный Roboto-Black 900; `RobotoFamily` из `:components` идёт только до 700, иначе faux-bold синтез — см. gotcha ниже). Уход каскадом: text fade 200мс → bg fade 300мс через два `Animatable<Float>`; alpha читается в `graphicsLayer { alpha = ... }` (draw scope, без рекомпозиции на кадр). `MainScaffold.enablePrewarm` гейтит pre-warm Spaces/Calls до закрытия splash'а — иначе тяжёлая AndroidView-композиция дёргает fadeOut. Min-duration 700мс. **Rotation:** `mainUiReady` / `splashGone` / `hidden` — `rememberSaveable`; на поворот `if (hidden) return` мгновенно отрезает рендер.
- **Outgoing call** — оверлей по `callContext: CallContext?` в `MainActivity` (паттерн Profile). `CallContext` (sealed `BrandMeet` / `P2PCall(chatId)` / `GroupCall(chatId)`) + `HistoryTarget` лежат в `:core:model` — иначе `:feature:chatdetail` пришлось бы зависеть от `:feature:calls`. Entry-points: «Новая встреча» в `CallsScreen` или `HeaderConfig.Chat.onCallClick` в чат-детале (P2P → persona.fullName, Group → chat.title). BottomTabs скрыты при `callContext != null`.

  **Архитектура**: `OutgoingCallScreen.kt` — тонкий диспетчер `if (P2P) P2POutgoingScreen else LobbyInCallScreen`. Каждый экран сам владеет VM + ExoPlayer + mount-флагами. Общий chrome — в **`CallChrome.kt`**: `CallHeader` (зеркалит правый кластер `flip+gap8+share=80dp` Spacer'ом 44dp слева когда `showFlip=true` — title попадает на абсолютную середину), `CallBottomBar` (5 кнопок, `Arrangement.spacedBy(16dp, Center)`), `ToggleButton` (OFF=white80+black95, ON=white10+white90), `CircleIconButton`, `EndCallButton`, `formatElapsed`.

  **Video**: `assets/videos/My_9_16.mp4` зацикленно через ExoPlayer (Media3 1.3.1) в `CallVideoPlayer.kt`. PlayerView инфлейтится из `R.layout.call_player_view` с `surface_type=texture_view` — нужно для P2P PiP rounded corners: SurfaceView рисуется hardware overlay'ем и игнорит Compose `Modifier.clip`, TextureView подчиняется. `resizeMode=RESIZE_MODE_ZOOM` дублируется программно (XML attr ненадёжно). Camera OFF → плеер pause + тёмный backdrop.

  **VM** (`OutgoingCallViewModel.kt`): `phase` (`Lobby | InCall(startElapsedMs)`), `toggles`, `elapsedMs`, `callConnected`, `someoneCameraOn`. **P2P** init: `camera=false`, сразу `phase=InCall(placeholder)`, ringingJob 2000ms → `callConnected=true` + reset startElapsedMs + старт таймера; `someoneCameraOn` остаётся false (включение через long-tap). **Group/BrandMeet**: `phase=Lobby`, `callConnected→true` после `onJoin()`. До connect P2P показывает «Вызов...»; EndCall до connect → `durationMs=0`. На end-call: `recordOutgoingCall(...)` всегда + `appendCallMeetMessage(chatId, ...)` для chat-triggered. Collapse/swipe/X — без записи.

  **P2P UI** (`P2POutgoingScreen` + `P2PInCallContent`): fade-in fullscreen (`CallFadeOverlay`). **4 состояния камер**: (off×off) тёмный фон + аватарка counterpart-а 120dp + name + status, `P2PSpeakingIndicator` стартует на `elapsedMs >= 2000L`; (on×off) self-video fullscreen; (off×on) аватарка counterpart-а fullscreen через `Image.Crop` (Bitmap из `LocalBitmapCache`); (on×on) + PiP self-video 100×150dp rounded 12dp в правом нижнем (20dp над кнопками, 16dp от правого края). Long-tap по аватарке/fullscreen-картинке counterpart-а → `onToggleSomeoneCamera()`. Header — Collapse + Share (без flip), `iconTint=white50` в state 1 (низкоконтрастный фон с аватаркой), `white100` если активно видео; title+timer в header только при `someoneCameraOn`.

  **P2PSpeakingIndicator** — 3 кольца audio-envelope вокруг аватарки. Envelope = сумма sin'ов от 4 НЕ-кратных периодов — голосовая амплитуда, не чистый sin. Кольца 1/2 синфазны (3.0s+1.3s), кольцо 3 на независимом ритме (2.2s+0.95s) — амплитуды не совпадают. Master gate-cycle 4700ms: 500ms ramp-up → 2.5s sustain → 700ms decay → 1s silence → seamless restart (initialValue=targetValue=0). Параметр `active` gate'ит весь stack через `if (active) rememberInfiniteTransition` — каждый триггер создаёт state свежим (envelope с 0, плавный ramp-up без подхвата в середине цикла).

  **Group/BrandMeet UI** (`LobbyInCallScreen` + `LobbyContent` + `InCallContent`): Lobby — modal bottom sheet (`LobbyBottomSheet.kt`, 100% высоты, swipe-down dismiss via `rememberDraggableState`). На Join sheet exit'ит вниз (240ms) **параллельно** с InCall slide-in справа (`InCallSlideOverlay.kt`, 280ms). `videoActive=false` на переходе отвязывает Lobby PlayerView (InCall монтирует свой с тем же `player`, last-write-wins без рейса). Lobby кнопка «Присоединиться» = 296dp (= ширине toggles-ряда 3×56+2×64).

  **Mount-during-exit**: `sheetMounted`/`incallMounted` поднимаются на enter, сбрасываются в `onExitComplete`. **`onClose` coordination**: `closedReported` guard вызывает onClose ровно раз, когда `isClosing && все mount-флаги false`.

  **IME-hide** (`MainActivity.kt`): пока `callContext != null` — `DisposableEffect` + `LifecycleEventObserver(ON_RESUME)` дёргают `WindowInsetsControllerCompat.hide(ime())`. Без этого после сворачивания с открытым звонком IME могла подняться при resume (focus EditText'а MessagePanel'а в underlying ChatDetail'е сохраняется).

  **Bubble `CallMeet`** subtitle: `TimeFormatter.formatCallDuration(durationMs)` → «`HH:mm, N мин`» (короткая «N сек/мин/ч»; `durationMs ≤ 0` → пусто).

  **TODO V2**: Collapse → PiP / mini-bar в `MainScaffold` (потребует вынести `callContext` в более долгоживущий holder, например `AppContainer`).

## Spec и план

- `docs/superpowers/specs/2026-05-13-android-template-design.md` — архитектурный дизайн
- `docs/superpowers/plans/2026-05-13-android-template-implementation.md` — implementation plan
- `docs/superpowers/specs/2026-06-30-quote-picker-style-segmented-design.md` — 3-стиль picker
- `docs/superpowers/plans/2026-06-30-quote-picker-style-segmented-implementation.md` — implementation plan
