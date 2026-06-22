# Outgoing call (lobby + in-call) — Design

**Date:** 2026-06-09
**Branch:** `feature/outgoing-call`
**Status:** Draft → spec review

## Goal

Имитировать исходящий видеозвонок: пред-вызов («lobby») с превью-видео, переключатели камеры/микрофона/динамика и кнопкой «Присоединиться»; затем in-call с таймером и кнопкой завершения. После завершения — добавить запись в историю вкладки Calls, а для звонков из чата дополнительно повесить `Message.CallMeet`-бабл с длительностью.

## Entry points

| Триггер | Маппинг в `CallContext` |
|---|---|
| Тап `CallActionTile("Новая встреча")` на `CallsScreen` | `BrandMeet` — title = `"{Brand} Meet"` (capitalized codename); historyTarget = self-persona (Anna Ivanova) |
| `HeaderConfig.Chat.onCallClick` в P2P-чате (`ChatType.P2P`) | `P2PCall(chatId)` — title = `persona.fullName`; historyTarget = persona |
| Тот же hook в Group/Channel-чате | `GroupCall(chatId)` — title = `chat.title`; historyTarget = `Group`-аватар c initials |

`CallActionTile("Создать ссылку")` остаётся no-op (вне scope).

## State machine

```
External: callContext: CallContext? in MainActivity
  null            → screen скрыт
  not null        → OutgoingCallScreen mounted полно-экран. поверх SplashOverlay/AppScaffold/ChatDetail/Profile

Internal in OutgoingCallViewModel:
  phase: Lobby | InCall(startElapsedMs: Long)
  toggles: { camera = true, mic = false, speaker = true }   // mute-on-join default

Transitions:
  Lobby + Join     → InCall(SystemClock.elapsedRealtime())
  Lobby + Close X  → exit (callContext = null), NO history record, NO chat bubble
  InCall + EndCall → exit, RECORD outgoing call:
                       - всегда: добавить Call в _calls (вкладка Calls)
                       - если chat-triggered (P2P / Group): добавить Message.CallMeet
                         в messages этого чата (status = ANSWERED)
  InCall + Collapse → exit, NO history record (V1)
                       FUTURE: minimize-to-PiP + "вернуться в звонок" pill в чат-листе.
```

`callContext = null` пока активен звонок выставляет `showBottomTabs = false` в MainActivity (BottomTabs скрываются как в Profile/QuotePicker).

## CallContext data model

```kotlin
sealed class CallContext {
    abstract val title: String          // header title
    abstract val historyTarget: HistoryTarget

    data class BrandMeet(
        override val title: String,                  // "Foxtrot Meet"
        override val historyTarget: HistoryTarget,   // self
    ) : CallContext()

    data class P2PCall(
        val chatId: String,
        override val title: String,                  // persona.fullName
        override val historyTarget: HistoryTarget,   // persona
    ) : CallContext()

    data class GroupCall(
        val chatId: String,
        override val title: String,                  // chat.title
        override val historyTarget: HistoryTarget,   // Group avatar
    ) : CallContext()
}

data class HistoryTarget(
    val id: String,
    val name: String,
    val avatar: AvatarSpec,
)
```

`chatId` нужен только в P2P/Group — туда уйдёт CallMeet-бабл.

## Архитектура (файлы)

**Расширяем `:feature:calls`** (новый модуль не создаём):

- `OutgoingCallContext.kt` — sealed class `CallContext` + `HistoryTarget`.
- `OutgoingCallViewModel.kt` — state (`phase`, `toggles`, `elapsedMs: StateFlow<Long>`), методы (`onJoin`, `onToggleCamera`, `onToggleMic`, `onToggleSpeaker`, `onEnd`, `onCancelLobby`, `onCollapse`).
- `OutgoingCallScreen.kt` — root composable: создаёт `ExoPlayer` через `remember`, рисует `CallVideoPlayer` на background, поверх `Crossfade(phase)` рендерит `LobbyContent` либо `InCallContent`.
- `LobbyContent.kt` — UI лобби.
- `InCallContent.kt` — UI in-call.
- `CallVideoPlayer.kt` — `AndroidView<PlayerView>` обёртка; на off-camera показывает `Box` с тёмным фоном и `AvatarView` 120dp в центре.

**`:app/MainActivity.kt`**:
- `var callContext by remember { mutableStateOf<CallContext?>(null) }`
- `OutgoingCallScreen(...)` рендерится как последний child outer-Box'а (поверх SplashOverlay'а; SplashOverlay сам уходит первым на cold-start, до Outgoing вряд ли дойдёт).
- `showBottomTabs = openChatId == null && !profileOpen && callContext == null`.
- Колбэки вниз:
  - `MainScaffold(..., onStartCall: (CallContext) -> Unit)`
  - `CallsScreen(..., onStartCall)` → `CallActionTile("Новая встреча", onClick = { onStartCall(BrandMeet(...)) })`
  - `ChatDetailScreen(..., onStartCall)` → `HeaderConfig.Chat.onCallClick = { onStartCall(P2PCall(chatId, ...) / GroupCall(chatId, ...)) }`

**`:core:data`**:
- `MessengerRepository`: добавляются методы
  - `fun recordOutgoingCall(target: HistoryTarget, durationMs: Long, isVideo: Boolean, isGroupCall: Boolean)`
  - `fun appendCallMeetMessage(chatId: String, durationMs: Long, isVideo: Boolean)`
- `MockRepositoryImpl`: 
  - первый pre-pend'ит новый `Call(type=Outgoing, ...)` в `_calls.value` (свежее сверху, in-memory)
  - второй append'ит `Message.CallMeet` в кэш messages этого чата + обновляет `chat.lastMessage` (`updateChatLastMessage` уже есть)

**Assets / Gradle**:
- `app/src/main/assets/videos/My_9_16.mp4` — **git-tracked** (вариант A). Скопировать вручную из `C:/testing/VideoCall/My_9_16.mp4` в репо, закоммитить.
- В `app/build.gradle.kts` добавить ExoPlayer deps:
  - `androidx.media3:media3-exoplayer:1.3.1`
  - `androidx.media3:media3-ui:1.3.1`
  - в `libs.versions.toml` соответствующие версии

## Видео-пайплайн

- `ExoPlayer` создаётся в `OutgoingCallScreen` через `remember { ExoPlayer.Builder(context).build() }`.
- `setMediaItem(MediaItem.fromUri("asset:///videos/My_9_16.mp4"))`, `repeatMode = REPEAT_MODE_ALL`, `volume = 0f` (mute), `prepare()`, `playWhenReady = toggles.camera`.
- `AndroidView<PlayerView>` рендерит full-screen background. `resizeMode = RESIZE_MODE_ZOOM` (≈ CENTER_CROP).
- `Crossfade(phase)` touches только UI-overlay (header + buttons), плеер не перетыкается между Lobby и InCall — видео идёт непрерывно.
- `DisposableEffect(Unit) { onDispose { player.release() } }` при размонтировании экрана.
- Camera OFF: `playWhenReady = false` (или `player.pause()`), `PlayerView` затемняется через overlay `Box(Modifier.background(Color(0xFF1A1A1A)))`. По центру — `AvatarView` 120dp, для Lobby = self-persona (Anna), для InCall = `context.historyTarget.avatar`.

## Визуал — Lobby

- Корневой `Box(Modifier.fillMaxSize().background(#1A1A1A))`. Видео обёрнуто `Modifier.statusBarsPadding().clip(RoundedCornerShape(topStart = lobbyCornerRadius, topEnd = lobbyCornerRadius))`, где `lobbyCornerRadius by animateDpAsState(if (phase is Lobby) 16.dp else 0.dp)`.
- Header overlay (`Modifier.statusBarsPadding().padding(top=16.dp, horizontal=8.dp)`):
  - X (`close`, 24dp) — left, tap → `onCancelLobby()` → `callContext = null`
  - Title (`Roboto Medium 16sp`, `white.copy(0.9f)`, drop-shadow `0x29000000, offset(0,2), blur 6`) — centered
  - Flip (`flip`, 24dp) — right, no-op
- «Присоединиться» — accent-pill (`brand.accentColor(isDark)` background, `Color.White` content, `Roboto Medium 18sp` `Subtitle 1 - M`), width 290dp, height 56dp, corner 1000dp, `Modifier.align(BottomCenter).padding(bottom = 100.dp + navBarsPadding)` (с зазором над bottom-кнопками).
- Под кнопкой ряд из 3 кнопок 56dp, `Arrangement.spacedBy(64.dp)`, центрирован:
  - Camera: ON = `Color.White.copy(0.6f)` bg + `video-on-call-filled` 32dp tint `Color.White` / OFF = `0.2f` + `video-off-call-filled`
  - Speaker: ON = `0.6f` + `volume` / OFF = `0.2f` + `speaker`
  - Mic: ON = `0.6f` + `microphone-on` / OFF = `0.2f` + `microphone-off`
- Ripple через `appClickable` (basicColor55 ripple — общий хелпер).

## Визуал — InCall

- Видео full-bleed без `statusBarsPadding` и без rounded corners (`lobbyCornerRadius` уже анимировался до 0).
- Header overlay (`Modifier.statusBarsPadding().padding(top=16.dp, horizontal=8.dp)`):
  - Collapse (`fullscreen-exit`, 24dp) — left, tap → `onCollapse()` → `callContext = null` (V1).
  - Title+timer column в центре:
    - Title (`Roboto Medium 16sp`, `white.copy(0.9f)`, shadow)
    - Timer «MM:SS» (`Roboto Regular 14sp`, `white.copy(0.9f)`, shadow)
  - Right cluster: flip (24dp, no-op) + share-android (24dp, no-op).
- Bottom row — 5 кнопок 56dp, `Modifier.fillMaxWidth().padding(horizontal=12.dp, bottom = 28.dp + navBarsPadding)`, `Arrangement.SpaceBetween`:
  - Camera / Speaker / Mic — те же toggle'ы, **состояние переносится из Lobby через VM** (один VM на жизненный цикл `callContext`).
  - Dots (`...`, `dots`, white 0.2% bg) — no-op (`appClickable` ripple), будущая точка для «Доп. меню».
  - End-call — `brand.dangerDefault()` bg, `call-ended-filled` 32dp `Color.White`, tap → `onEnd()`.

## Таймер

- `viewModelScope.launch { while (isActive) { _elapsedMs.value = SystemClock.elapsedRealtime() - startElapsedMs; delay(1000) } }`. Job ссылка `timerJob: Job?` отмена в `onEnd()`/`onCollapse()`.
- В `InCallContent` форматирование `String.format("%02d:%02d", min, sec)`.

## Сохранение в историю

- `OutgoingCallViewModel.onEnd()`:
  1. `durationMs = elapsedRealtime() - startElapsedMs`
  2. `timerJob.cancel()`
  3. `repo.recordOutgoingCall(context.historyTarget, durationMs, isVideo=true, isGroupCall = context is GroupCall)`
  4. Если `context is P2PCall || context is GroupCall`: `repo.appendCallMeetMessage(context.chatId, durationMs, isVideo=true)`
  5. `onClose()` (закрывает экран в MainActivity)

`Message.CallMeet` структура (уже есть в `core.model:Message.kt:124-138`):
- `callStatus = CallStatus.Answered` (мы дошли до in-call → call considered answered)
- `durationMs` = из таймера
- `isVideo = true`
- `isGroupCall = context is GroupCall`
- `isMine = true` (звонок исходящий, от нас)
- `chatId, senderId = currentUser.id, timestamp = now, id = новый uuid` — стандартный набор полей Message
- `status = MessageStatus.DELIVERED` (как и для обычных отправок — см. CLAUDE.md «Поведение отправки»)

## Бренд / тема

- Видео-чёрный фон + полупрозрачные оверлеи кнопок hardcode'ятся (`Color.White.copy(alpha=...)`) — не зависят от бренд-палитры.
- **Accent-кнопка** «Присоединиться» = `brand.accentColor(isDark)`.
- **Danger-кнопка** end-call = `brand.dangerDefault()`.
- Статус-бар на время звонка форсится `isAppearanceLightStatusBars = false` (dark icons на тёмном видео не видны), в `DisposableEffect(callContext)` MainActivity.

## Out of scope (V1)

- Реальная камера и микрофон.
- Flip / share / dots — кнопки видны и кликабельны (ripple), но логики нет.
- **Collapse → PiP**: в V1 collapse просто закрывает звонок без записи. **TODO для V2:** свернуть звонок в picture-in-picture или mini-bar в `MainScaffold`, в `ChatList` поверх top-app-bar повесить кнопку «Вернуться в звонок» (по аналогии с Telegram/WhatsApp). Состояние `callContext` придётся переселить из transient state MainActivity в более долгоживущий (AppContainer? отдельный CallController?).
- Создание ссылки на встречу (`CallActionTile("Создать ссылку")` остаётся no-op).
- Реальный playback / запись звука — `ExoPlayer.volume = 0f` всё время.
- Sync-таска для видео — выбран git-track вариант, видео живёт в репе.
- Уведомления / heads-up / системный call screen — звонок чисто внутри-приложения.

## Тестирование

- `core/data`: юнит-тесты на `recordOutgoingCall` (добавление в начало `_calls`) и `appendCallMeetMessage` (правильный `Message.CallMeet`, обновлённый `lastMessage`).
- UI-тесты не предусматриваются (как и для других экранов в этом прототипе).

## Открытые точки реализации (для плана)

- Имя констант: `LOBBY_TOGGLE_BUTTON_SIZE_DP = 56`, `LOBBY_JOIN_BUTTON_WIDTH_DP = 290`, `LOBBY_TOGGLES_GAP_DP = 64`, `IN_CALL_BUTTON_PADDING_DP = 12`, etc. — конкретный inventory выровняется при имплементации.
- В `MockRepositoryImpl.appendCallMeetMessage` нужно убедиться, что cache для `chatId` уже загружен (или загрузить лениво) перед append'ом — иначе бабл потеряется.
- Если для нового `Message.CallMeet`-бабла потребуется специфичный конструктор — проверить `Message.CallMeet`-data class на полные поля (`id`, `senderId = currentUser.id`, `timestamp = now`, `status = ANSWERED`).
