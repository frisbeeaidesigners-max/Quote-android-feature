# Outgoing call (lobby + in-call) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Имитировать исходящий видеозвонок — два экрана (Lobby + InCall) с переключателями камеры/микрофона/динамика, реальным таймером в InCall, фоновой петлёй mock-видео через ExoPlayer; после end-call — записать `Call` в историю + (для chat-triggered) `Message.CallMeet`-бабл в чат.

**Architecture:** Fullscreen-оверлей в `MainActivity` (state-driven `callContext`, паттерн Profile). Внутри — один `OutgoingCallViewModel` держит phase (`Lobby | InCall`), toggle'ы и таймер; `Crossfade(phase)` переключает UI поверх единого `CallVideoPlayer` (видео не перетыкается между фазами). Все плумбинг-колбэки через лямбды (как `onChatClick`).

**Tech Stack:** Kotlin 1.9.22 · Jetpack Compose (BOM 2024.02.00) · ExoPlayer (Media3 1.3.1) · JUnit 4 для unit-тестов в `:core:data`.

**Spec:** [`docs/superpowers/specs/2026-06-09-outgoing-call-design.md`](../specs/2026-06-09-outgoing-call-design.md)

---

## File Structure

**Create:**
- `app/src/main/assets/videos/My_9_16.mp4` — git-tracked mock-видео.
- `feature/calls/src/main/java/com/example/template/feature/calls/OutgoingCallContext.kt` — sealed class `CallContext` + `HistoryTarget`.
- `feature/calls/src/main/java/com/example/template/feature/calls/OutgoingCallViewModel.kt` — VM (phase, toggles, elapsedMs, save-history).
- `feature/calls/src/main/java/com/example/template/feature/calls/OutgoingCallScreen.kt` — root: ExoPlayer + Crossfade(Lobby/InCall).
- `feature/calls/src/main/java/com/example/template/feature/calls/LobbyContent.kt` — UI лобби.
- `feature/calls/src/main/java/com/example/template/feature/calls/InCallContent.kt` — UI in-call.
- `feature/calls/src/main/java/com/example/template/feature/calls/CallVideoPlayer.kt` — AndroidView-обёртка PlayerView + off-state avatar overlay.
- `core/data/src/test/java/com/example/template/core/data/OutgoingCallRepositoryTest.kt` — unit-тесты для билдеров.

**Modify:**
- `gradle/libs.versions.toml` — добавить media3 версии и aliases.
- `app/build.gradle.kts` — добавить media3 deps.
- `core/model/src/main/java/com/example/template/core/model/Call.kt` (если потребуется extension — пока нет).
- `core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt` — новый interface methods.
- `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt` — impl + builder helpers в `companion object`.
- `feature/calls/src/main/java/com/example/template/feature/calls/CallsScreen.kt` — `onStartCall` param, проброс в action-tile.
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt` — `onStartCall` param, проброс в `HeaderConfig.Chat.onCallClick`.
- `app/src/main/java/com/example/template/MainScaffold.kt` — `onStartCall` param, пробрасывается дальше.
- `app/src/main/java/com/example/template/MainActivity.kt` — `callContext` state, render `OutgoingCallScreen`, hide BottomTabs.

---

### Task 1: Видеофайл + ExoPlayer-зависимости

**Files:**
- Create: `app/src/main/assets/videos/My_9_16.mp4` (копия из `C:/testing/VideoCall/My_9_16.mp4`)
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Скопировать видео в repo**

```bash
mkdir -p app/src/main/assets/videos/
cp C:/testing/VideoCall/My_9_16.mp4 app/src/main/assets/videos/My_9_16.mp4
ls -lh app/src/main/assets/videos/My_9_16.mp4
```

Expected: файл скопирован, ~470 KB.

- [ ] **Step 2: Добавить media3 версию в libs.versions.toml**

В `[versions]` блок (рядом с `compose-bom`):

```toml
media3 = "1.3.1"
```

В `[libraries]` блок (отсортировано алфавитно по alias):

```toml
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
```

- [ ] **Step 3: Подключить deps в `app/build.gradle.kts`**

В блок `dependencies { ... }` (рядом с другими `androidx-*` deps):

```kotlin
implementation(libs.androidx.media3.exoplayer)
implementation(libs.androidx.media3.ui)
```

- [ ] **Step 4: Проверить сборку**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` (deps скачиваются если их ещё нет).

- [ ] **Step 5: Коммит**

```bash
git add app/src/main/assets/videos/My_9_16.mp4 gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat(deps): add ExoPlayer (Media3 1.3.1) + mock call video"
```

---

### Task 2: `CallContext` + `HistoryTarget` data classes

**Files:**
- Create: `feature/calls/src/main/java/com/example/template/feature/calls/OutgoingCallContext.kt`

- [ ] **Step 1: Создать файл с моделью**

```kotlin
package com.example.template.feature.calls

import com.example.template.core.model.AvatarSpec

/**
 * Контекст исходящего звонка — задаёт title в хедере и кого вписать в историю звонков
 * после end-call. Создаётся в момент тапа на entry-point (Calls «Новая встреча» /
 * ChatHeader.Call) и передаётся в `MainActivity.callContext`.
 */
sealed class CallContext {
    abstract val title: String
    abstract val historyTarget: HistoryTarget

    /** Brand-meet из вкладки «Звонки» — title = "{Brand} Meet", chat-бабла нет. */
    data class BrandMeet(
        override val title: String,
        override val historyTarget: HistoryTarget,
    ) : CallContext()

    /** P2P-чат — title = persona.fullName, chatId нужен для CallMeet-бабла. */
    data class P2PCall(
        val chatId: String,
        override val title: String,
        override val historyTarget: HistoryTarget,
    ) : CallContext()

    /** Group/Channel — title = chat.title, chatId нужен для CallMeet-бабла. */
    data class GroupCall(
        val chatId: String,
        override val title: String,
        override val historyTarget: HistoryTarget,
    ) : CallContext()
}

/**
 * Цель для записи в историю Calls. id/name/avatar — то, что попадает в [com.example.template.core.model.Call]
 * (`counterpartId`/`counterpartName`/`counterpartAvatar`).
 */
data class HistoryTarget(
    val id: String,
    val name: String,
    val avatar: AvatarSpec,
)
```

- [ ] **Step 2: Проверить сборку модуля**

```bash
./gradlew :feature:calls:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Коммит**

```bash
git add feature/calls/src/main/java/com/example/template/feature/calls/OutgoingCallContext.kt
git commit -m "feat(calls): add CallContext + HistoryTarget data models"
```

---

### Task 3: Repository — builder-helpers + методы записи (TDD)

**Files:**
- Modify: `core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt`
- Modify: `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt`
- Test: `core/data/src/test/java/com/example/template/core/data/OutgoingCallRepositoryTest.kt`

- [ ] **Step 1: Написать падающие тесты — pure builders**

Создать `core/data/src/test/java/com/example/template/core/data/OutgoingCallRepositoryTest.kt`:

```kotlin
package com.example.template.core.data

import com.example.template.core.model.AvatarSpec
import com.example.template.core.model.AvatarType
import com.example.template.core.model.CallStatus
import com.example.template.core.model.CallType
import com.example.template.core.model.Message
import com.example.template.core.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingCallRepositoryTest {

    private val target = MockRepositoryImpl.HistoryTargetMock(
        id = "u-01",
        name = "Мария Климова",
        avatar = AvatarSpec(type = AvatarType.Person, initials = "МК", gradientIndex = 1),
    )

    @Test
    fun `buildOutgoingCallRecord creates Outgoing Call with given target`() {
        val call = MockRepositoryImpl.buildOutgoingCallRecord(
            target = target,
            durationMs = 90_000L,
            isVideo = true,
            isGroupCall = false,
            timestamp = 1_700_000_000_000L,
            id = "call-test",
        )
        assertEquals(CallType.Outgoing, call.type)
        assertEquals("u-01", call.counterpartId)
        assertEquals("Мария Климова", call.counterpartName)
        assertEquals("МК", call.counterpartAvatar.initials)
        assertEquals(90_000L, call.durationMs)
        assertTrue(call.isVideo)
        assertEquals(false, call.isGroupCall)
        assertEquals(1_700_000_000_000L, call.timestamp)
        assertEquals("call-test", call.id)
    }

    @Test
    fun `buildCallMeetMessage creates Answered CallMeet with duration`() {
        val msg = MockRepositoryImpl.buildCallMeetMessage(
            chatId = "c-1",
            senderId = "u-self",
            durationMs = 90_000L,
            isVideo = true,
            isGroupCall = false,
            timestamp = 1_700_000_000_000L,
            id = "m-test",
        ) as Message.CallMeet
        assertEquals("c-1", msg.chatId)
        assertEquals("u-self", msg.senderId)
        assertEquals(CallStatus.Answered, msg.callStatus)
        assertEquals(90_000L, msg.durationMs)
        assertTrue(msg.isVideo)
        assertEquals(false, msg.isGroupCall)
        assertEquals(MessageStatus.DELIVERED, msg.status)
        assertEquals(true, msg.isMine)
        assertEquals(1_700_000_000_000L, msg.timestamp)
    }
}
```

- [ ] **Step 2: Запустить тест — должен упасть с unresolved reference**

```bash
./gradlew :core:data:testDebugUnitTest --tests "*OutgoingCallRepositoryTest*"
```

Expected: FAIL — `MockRepositoryImpl.HistoryTargetMock` / `buildOutgoingCallRecord` / `buildCallMeetMessage` unresolved.

- [ ] **Step 3: Добавить builders в `MockRepositoryImpl.companion object`**

В файле `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt` добавить (или расширить существующий) `companion object` (в конце класса перед закрывающей скобкой):

```kotlin
companion object {
    /**
     * Test-only лёгкий аналог `feature.calls.HistoryTarget` — :core:data не зависит от
     * :feature:calls, поэтому маршрут передаётся через primitive-поля, либо через эту
     * dataclass. На runtime feature/calls передаёт свои поля напрямую в [recordOutgoingCall].
     */
    data class HistoryTargetMock(
        val id: String,
        val name: String,
        val avatar: com.example.template.core.model.AvatarSpec,
    )

    fun buildOutgoingCallRecord(
        target: HistoryTargetMock,
        durationMs: Long,
        isVideo: Boolean,
        isGroupCall: Boolean,
        timestamp: Long,
        id: String,
    ): com.example.template.core.model.Call = com.example.template.core.model.Call(
        id = id,
        type = com.example.template.core.model.CallType.Outgoing,
        counterpartId = target.id,
        counterpartName = target.name,
        counterpartAvatar = target.avatar,
        isVideo = isVideo,
        isGroupCall = isGroupCall,
        timestamp = timestamp,
        durationMs = durationMs,
    )

    fun buildCallMeetMessage(
        chatId: String,
        senderId: String,
        durationMs: Long,
        isVideo: Boolean,
        isGroupCall: Boolean,
        timestamp: Long,
        id: String,
    ): com.example.template.core.model.Message = com.example.template.core.model.Message.CallMeet(
        id = id,
        chatId = chatId,
        senderId = senderId,
        timestamp = timestamp,
        isMine = true,
        status = com.example.template.core.model.MessageStatus.DELIVERED,
        callStatus = com.example.template.core.model.CallStatus.Answered,
        isVideo = isVideo,
        isGroupCall = isGroupCall,
        durationMs = durationMs,
    )
}
```

Существующий `previewFromLast(...)` нужно оставить — добавляем в тот же companion.

- [ ] **Step 4: Запустить тест — должен пройти**

```bash
./gradlew :core:data:testDebugUnitTest --tests "*OutgoingCallRepositoryTest*"
```

Expected: PASS (2 tests).

- [ ] **Step 5: Добавить methods в `MessengerRepository` interface**

В файле `core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt` добавить в конце класса:

```kotlin
suspend fun recordOutgoingCall(
    counterpartId: String,
    counterpartName: String,
    counterpartAvatar: AvatarSpec,
    durationMs: Long,
    isVideo: Boolean,
    isGroupCall: Boolean,
)

suspend fun appendCallMeetMessage(
    chatId: String,
    durationMs: Long,
    isVideo: Boolean,
    isGroupCall: Boolean,
)
```

Добавить import `com.example.template.core.model.AvatarSpec` если его нет.

- [ ] **Step 6: Реализовать в `MockRepositoryImpl`**

В файле `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt` (после существующих send*-методов, например после `sendVoiceMessage`):

```kotlin
override suspend fun recordOutgoingCall(
    counterpartId: String,
    counterpartName: String,
    counterpartAvatar: com.example.template.core.model.AvatarSpec,
    durationMs: Long,
    isVideo: Boolean,
    isGroupCall: Boolean,
) {
    val now = System.currentTimeMillis()
    val call = buildOutgoingCallRecord(
        target = HistoryTargetMock(counterpartId, counterpartName, counterpartAvatar),
        durationMs = durationMs,
        isVideo = isVideo,
        isGroupCall = isGroupCall,
        timestamp = now,
        id = "call-$now",
    )
    // Pre-pend: свежий звонок наверху списка (CallsViewModel сортирует по timestamp DESC,
    // но pre-pend помогает если timestamp одинаковый).
    _calls.value = listOf(call) + _calls.value
}

override suspend fun appendCallMeetMessage(
    chatId: String,
    durationMs: Long,
    isVideo: Boolean,
    isGroupCall: Boolean,
) {
    val cache = messageCache[chatId] ?: return
    val chat = _chats.value.firstOrNull { it.id == chatId } ?: return
    val now = System.currentTimeMillis()
    val msg = buildCallMeetMessage(
        chatId = chatId,
        senderId = _currentUser.value.id,
        durationMs = durationMs,
        isVideo = isVideo,
        isGroupCall = isGroupCall,
        timestamp = now,
        id = "m-callmeet-$now",
    )
    cache.value = cache.value + msg
    updateChatLastMessage(chatId, msg)
}
```

- [ ] **Step 7: Запустить ВСЕ тесты `:core:data`**

```bash
./gradlew :core:data:testDebugUnitTest
```

Expected: PASS (все existing + 2 новых).

- [ ] **Step 8: Коммит**

```bash
git add core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt \
        core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt \
        core/data/src/test/java/com/example/template/core/data/OutgoingCallRepositoryTest.kt
git commit -m "feat(core-data): recordOutgoingCall + appendCallMeetMessage + builders + tests"
```

---

### Task 4: `OutgoingCallViewModel`

**Files:**
- Create: `feature/calls/src/main/java/com/example/template/feature/calls/OutgoingCallViewModel.kt`

- [ ] **Step 1: Создать ViewModel**

```kotlin
package com.example.template.feature.calls

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.template.core.data.MessengerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Phase оверлея звонка. `InCall.startElapsedMs` хранит SystemClock.elapsedRealtime() в момент
 * нажатия «Присоединиться» — от этого считаем длительность таймера и итоговый durationMs.
 */
sealed class CallPhase {
    data object Lobby : CallPhase()
    data class InCall(val startElapsedMs: Long) : CallPhase()
}

/** Состояние трёх toggle-кнопок. Camera ON по умолчанию, mic OFF (mute-on-join), speaker ON. */
data class CallToggles(
    val camera: Boolean = true,
    val mic: Boolean = false,
    val speaker: Boolean = true,
)

class OutgoingCallViewModel(
    val context: CallContext,
    private val repository: MessengerRepository,
) : ViewModel() {

    private val _phase = MutableStateFlow<CallPhase>(CallPhase.Lobby)
    val phase: StateFlow<CallPhase> = _phase.asStateFlow()

    private val _toggles = MutableStateFlow(CallToggles())
    val toggles: StateFlow<CallToggles> = _toggles.asStateFlow()

    /** Тикает раз в секунду пока phase == InCall. В Lobby и после end — 0. */
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private var timerJob: Job? = null

    fun onToggleCamera() {
        _toggles.value = _toggles.value.copy(camera = !_toggles.value.camera)
    }

    fun onToggleMic() {
        _toggles.value = _toggles.value.copy(mic = !_toggles.value.mic)
    }

    fun onToggleSpeaker() {
        _toggles.value = _toggles.value.copy(speaker = !_toggles.value.speaker)
    }

    fun onJoin() {
        if (_phase.value !is CallPhase.Lobby) return
        val start = SystemClock.elapsedRealtime()
        _phase.value = CallPhase.InCall(startElapsedMs = start)
        timerJob = viewModelScope.launch {
            while (isActive) {
                _elapsedMs.value = SystemClock.elapsedRealtime() - start
                delay(1000L)
            }
        }
    }

    /** Вызывается на X в Lobby или на Collapse в InCall — без записи в history. */
    fun onDismissWithoutRecording() {
        timerJob?.cancel()
        timerJob = null
    }

    /** Вызывается на end-call (красная кнопка) в InCall — записываем durationMs. */
    fun onEndCall() {
        val phaseSnapshot = _phase.value
        timerJob?.cancel()
        timerJob = null
        if (phaseSnapshot !is CallPhase.InCall) return
        val durationMs = SystemClock.elapsedRealtime() - phaseSnapshot.startElapsedMs
        viewModelScope.launch {
            repository.recordOutgoingCall(
                counterpartId = context.historyTarget.id,
                counterpartName = context.historyTarget.name,
                counterpartAvatar = context.historyTarget.avatar,
                durationMs = durationMs,
                isVideo = true,
                isGroupCall = context is CallContext.GroupCall,
            )
            val chatIdForBubble = when (val c = context) {
                is CallContext.P2PCall -> c.chatId
                is CallContext.GroupCall -> c.chatId
                is CallContext.BrandMeet -> null
            }
            if (chatIdForBubble != null) {
                repository.appendCallMeetMessage(
                    chatId = chatIdForBubble,
                    durationMs = durationMs,
                    isVideo = true,
                    isGroupCall = context is CallContext.GroupCall,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
```

- [ ] **Step 2: Проверить сборку**

```bash
./gradlew :feature:calls:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Коммит**

```bash
git add feature/calls/src/main/java/com/example/template/feature/calls/OutgoingCallViewModel.kt
git commit -m "feat(calls): OutgoingCallViewModel — phase, toggles, timer, history save"
```

---

### Task 5: `CallVideoPlayer` composable

**Files:**
- Create: `feature/calls/src/main/java/com/example/template/feature/calls/CallVideoPlayer.kt`

- [ ] **Step 1: Создать composable**

```kotlin
package com.example.template.feature.calls

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.components.avatar.AvatarColorScheme
import com.example.components.avatar.AvatarView
import com.example.template.core.model.AvatarSpec
import com.example.template.core.model.AvatarType
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalBitmapCache
import com.example.template.core.ui.LocalIsDark

/**
 * Видео-плеер для outgoing-call экрана. Циклит mock-видео из app-assets, mute'd. Когда
 * `cameraOn=false` — pause + накладывается тёмный overlay c 120dp аватаркой `offStateAvatar`
 * по центру.
 *
 * Плеер передаётся СНАРУЖИ (создаётся в OutgoingCallScreen и переиспользуется между
 * Lobby/InCall) — здесь только биндим к PlayerView и управляем playWhenReady.
 */
@OptIn(UnstableApi::class)
@Composable
fun CallVideoPlayer(
    player: ExoPlayer,
    cameraOn: Boolean,
    offStateAvatar: AvatarSpec,
    modifier: Modifier = Modifier,
) {
    // Управляем играем/не играем плеером.
    DisposableEffect(player, cameraOn) {
        player.playWhenReady = cameraOn
        onDispose { /* плеер не отпускаем здесь — owner OutgoingCallScreen */ }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setPlayer(player)
                }
            },
            update = { v -> v.player = player },
        )
        if (!cameraOn) {
            // Затемнение поверх плеера + аватарка 120dp по центру.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center,
            ) {
                AvatarOverlay(spec = offStateAvatar, sizeDp = 120)
            }
        }
    }
}

/**
 * Аватарка 120dp поверх off-state экрана. Использует :components AvatarView с
 * brand-aware ColorScheme и BitmapCache для аватарок с image-asset'ом.
 */
@Composable
private fun AvatarOverlay(spec: AvatarSpec, sizeDp: Int) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val bitmapCache = LocalBitmapCache.current
    @Suppress("UNUSED_VARIABLE")
    val cacheVersion = bitmapCache.version.value
    val gradientPairs = remember(brand, isDark) { brand.avatarGradientPairs(isDark) }
    val gradient = gradientPairs[spec.gradientIndex.coerceIn(0, gradientPairs.lastIndex)]
    val scheme = remember(brand, isDark, gradient) {
        brand.avatarColorScheme(isDark).copy(
            initialsGradientTop = gradient.top,
            initialsGradientBottom = gradient.bottom,
        )
    }
    val image = bitmapCache.get(spec.imageAsset)
    val type = when {
        spec.type == AvatarType.Self -> AvatarView.AvatarViewType.SAVED
        image != null -> AvatarView.AvatarViewType.IMAGE
        else -> AvatarView.AvatarViewType.INITIALS
    }
    AndroidView(
        modifier = Modifier.size(sizeDp.dp),
        factory = { ctx -> AvatarView(ctx) },
        update = { v ->
            v.configure(
                mode = AvatarView.AvatarMode.USER_GROUP,
                type = type,
                size = AvatarView.AvatarSize.SIZE_120,
                text = spec.initials.orEmpty(),
                image = image,
                colorScheme = scheme,
            )
        },
    )
}
```

- [ ] **Step 2: Проверить сборку модуля**

```bash
./gradlew :feature:calls:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Коммит**

```bash
git add feature/calls/src/main/java/com/example/template/feature/calls/CallVideoPlayer.kt
git commit -m "feat(calls): CallVideoPlayer + AvatarOverlay (camera-off state)"
```

---

### Task 6: `LobbyContent` composable

**Files:**
- Create: `feature/calls/src/main/java/com/example/template/feature/calls/LobbyContent.kt`

- [ ] **Step 1: Создать UI лобби**

```kotlin
package com.example.template.feature.calls

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.designsystem.DSTypography
import com.example.components.designsystem.dsIconPainter
import com.example.components.designsystem.toComposeTextStyle
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appClickable

/**
 * UI лобби. ОНТО (поверх плеера в OutgoingCallScreen) — только header + Join + 3 toggle.
 * Сам плеер живёт уровнем выше.
 */
@Composable
fun LobbyContent(
    title: String,
    toggles: CallToggles,
    onToggleCamera: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onJoin: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val accent = Color(brand.accentColor(isDark))

    Box(modifier = modifier.fillMaxSize()) {
        // Header: X | Title | Flip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 16.dp, start = 8.dp, end = 8.dp)
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(iconName = "close", onClick = onClose)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.9f),
                    style = DSTypography.body3M.toComposeTextStyle(),
                    maxLines = 1,
                )
            }
            IconCircleButton(iconName = "flip", onClick = { /* no-op V1 */ })
        }

        // Join button + bottom toggles
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            // Toggles row (под Join)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 0.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                ToggleButton(
                    iconName = if (toggles.camera) "video-on-call-filled" else "video-off-call-filled",
                    isActive = toggles.camera,
                    onClick = onToggleCamera,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.width(64.dp))
                ToggleButton(
                    iconName = if (toggles.speaker) "volume" else "speaker",
                    isActive = toggles.speaker,
                    onClick = onToggleSpeaker,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.width(64.dp))
                ToggleButton(
                    iconName = if (toggles.mic) "microphone-on" else "microphone-off",
                    isActive = toggles.mic,
                    onClick = onToggleMic,
                )
            }
        }

        // Join — расположена над toggle-рядом
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp + 56.dp + 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(290.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(1000.dp))
                    .background(accent)
                    .appClickable(onClick = onJoin),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Присоединиться",
                    color = Color.White,
                    style = DSTypography.subtitle1M.toComposeTextStyle().copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun IconCircleButton(
    iconName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(1000.dp))
            .appClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val icon = dsIconPainter(iconName, sizeDp = 24f)
        if (icon != null) {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
    }
}

@Composable
internal fun ToggleButton(
    iconName: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val bgAlpha = if (isActive) 0.6f else 0.2f
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(1000.dp))
            .background(Color.White.copy(alpha = bgAlpha))
            .appClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val icon = dsIconPainter(iconName, sizeDp = 32f)
        if (icon != null) {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
    }
}
```

- [ ] **Step 2: Проверить сборку**

```bash
./gradlew :feature:calls:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Коммит**

```bash
git add feature/calls/src/main/java/com/example/template/feature/calls/LobbyContent.kt
git commit -m "feat(calls): LobbyContent — header + Join + 3 toggles"
```

---

### Task 7: `InCallContent` composable

**Files:**
- Create: `feature/calls/src/main/java/com/example/template/feature/calls/InCallContent.kt`

- [ ] **Step 1: Создать UI in-call**

```kotlin
package com.example.template.feature.calls

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.components.designsystem.DSTypography
import com.example.components.designsystem.dsIconPainter
import com.example.components.designsystem.toComposeTextStyle
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.appClickable

@Composable
fun InCallContent(
    title: String,
    elapsedMs: Long,
    toggles: CallToggles,
    onToggleCamera: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onCollapse: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val danger = Color(brand.dangerDefault())

    Box(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 16.dp, start = 8.dp, end = 8.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(iconName = "fullscreen-exit", onClick = onCollapse)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = title,
                        color = Color.White.copy(alpha = 0.9f),
                        style = DSTypography.body3M.toComposeTextStyle(),
                        maxLines = 1,
                    )
                    Text(
                        text = formatElapsed(elapsedMs),
                        color = Color.White.copy(alpha = 0.9f),
                        style = DSTypography.body5R.toComposeTextStyle(),
                    )
                }
            }
            CircleIconButton(iconName = "flip", onClick = { /* no-op V1 */ })
            Spacer(Modifier.size(4.dp))
            CircleIconButton(iconName = "share-android", onClick = { /* no-op V1 */ })
        }

        // Bottom buttons — 5 в ряд
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleButton(
                iconName = if (toggles.camera) "video-on-call-filled" else "video-off-call-filled",
                isActive = toggles.camera,
                onClick = onToggleCamera,
            )
            ToggleButton(
                iconName = if (toggles.speaker) "volume" else "speaker",
                isActive = toggles.speaker,
                onClick = onToggleSpeaker,
            )
            ToggleButton(
                iconName = if (toggles.mic) "microphone-on" else "microphone-off",
                isActive = toggles.mic,
                onClick = onToggleMic,
            )
            ToggleButton(iconName = "dots", isActive = false, onClick = { /* no-op */ })
            EndCallButton(background = danger, onClick = onEndCall)
        }
    }
}

@Composable
private fun CircleIconButton(iconName: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(1000.dp))
            .appClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val icon = dsIconPainter(iconName, sizeDp = 24f)
        if (icon != null) {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
    }
}

@Composable
private fun EndCallButton(background: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(1000.dp))
            .background(background)
            .appClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val icon = dsIconPainter("call-ended-filled", sizeDp = 32f)
        if (icon != null) {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}
```

- [ ] **Step 2: Проверить сборку**

```bash
./gradlew :feature:calls:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Коммит**

```bash
git add feature/calls/src/main/java/com/example/template/feature/calls/InCallContent.kt
git commit -m "feat(calls): InCallContent — header+timer + 5 bottom buttons"
```

---

### Task 8: `OutgoingCallScreen` root — ExoPlayer + Crossfade

**Files:**
- Create: `feature/calls/src/main/java/com/example/template/feature/calls/OutgoingCallScreen.kt`

- [ ] **Step 1: Создать root composable**

```kotlin
package com.example.template.feature.calls

import androidx.annotation.OptIn
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.template.core.data.MessengerRepository
import com.example.template.core.model.AvatarSpec
import com.example.template.core.model.AvatarType

private const val ASSET_VIDEO_URI = "asset:///videos/My_9_16.mp4"

/**
 * Корневой композабл оверлея звонка. Создаёт один ExoPlayer на жизненный цикл экрана,
 * рендерит фоновое видео через [CallVideoPlayer], поверх через Crossfade переключает
 * Lobby/InCall.
 *
 * Self-аватарка для Lobby берётся из текущего пользователя через [selfAvatarSpec]
 * (передаётся снаружи, чтобы не тянуть в этот файл зависимость на repository).
 */
@OptIn(UnstableApi::class)
@Composable
fun OutgoingCallScreen(
    context: CallContext,
    selfAvatar: AvatarSpec,
    repository: MessengerRepository,
    onClose: () -> Unit,
) {
    val viewModel = remember(context, repository) {
        OutgoingCallViewModel(context = context, repository = repository)
    }
    val phase by viewModel.phase.collectAsState()
    val toggles by viewModel.toggles.collectAsState()
    val elapsedMs by viewModel.elapsedMs.collectAsState()

    val androidCtx = LocalContext.current
    val player = remember(androidCtx) {
        ExoPlayer.Builder(androidCtx).build().apply {
            setMediaItem(MediaItem.fromUri(ASSET_VIDEO_URI))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Lobby — закругление верхних углов и status-bar inset. InCall — full-bleed.
    val isLobby = phase is CallPhase.Lobby
    val cornerRadius by animateDpAsState(
        targetValue = if (isLobby) 16.dp else 0.dp,
        animationSpec = tween(durationMillis = 240),
        label = "lobby-corner",
    )

    // Off-state avatar: в lobby — self, в InCall — counterpart.
    val offAvatar = if (isLobby) selfAvatar else context.historyTarget.avatar

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
        // Видео-плеер (фон). В lobby с верхним отступом + clip; в InCall edge-to-edge.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .let { if (isLobby) it.statusBarsPadding() else it }
                .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)),
        ) {
            CallVideoPlayer(
                player = player,
                cameraOn = toggles.camera,
                offStateAvatar = offAvatar,
            )
        }

        // Phase overlay.
        Crossfade(targetState = isLobby, animationSpec = tween(240), label = "phase-crossfade") { lobby ->
            if (lobby) {
                LobbyContent(
                    title = context.title,
                    toggles = toggles,
                    onToggleCamera = viewModel::onToggleCamera,
                    onToggleMic = viewModel::onToggleMic,
                    onToggleSpeaker = viewModel::onToggleSpeaker,
                    onJoin = viewModel::onJoin,
                    onClose = {
                        viewModel.onDismissWithoutRecording()
                        onClose()
                    },
                )
            } else {
                InCallContent(
                    title = context.title,
                    elapsedMs = elapsedMs,
                    toggles = toggles,
                    onToggleCamera = viewModel::onToggleCamera,
                    onToggleMic = viewModel::onToggleMic,
                    onToggleSpeaker = viewModel::onToggleSpeaker,
                    onCollapse = {
                        viewModel.onDismissWithoutRecording()
                        onClose()
                    },
                    onEndCall = {
                        viewModel.onEndCall()
                        onClose()
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 2: Проверить сборку**

```bash
./gradlew :feature:calls:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Коммит**

```bash
git add feature/calls/src/main/java/com/example/template/feature/calls/OutgoingCallScreen.kt
git commit -m "feat(calls): OutgoingCallScreen root — ExoPlayer + Crossfade(Lobby/InCall)"
```

---

### Task 9: Wire MainActivity + MainScaffold + CallsScreen

**Files:**
- Modify: `app/src/main/java/com/example/template/MainScaffold.kt`
- Modify: `feature/calls/src/main/java/com/example/template/feature/calls/CallsScreen.kt`
- Modify: `app/src/main/java/com/example/template/MainActivity.kt`

- [ ] **Step 1: Добавить `onStartCall` параметр в MainScaffold**

В `MainScaffold.kt`, в сигнатуре `fun MainScaffold` (после `enablePrewarm`):

```kotlin
@Composable
fun MainScaffold(
    onChatClick: (String) -> Unit,
    onProfileClick: () -> Unit = {},
    showBottomTabs: Boolean = true,
    enablePrewarm: Boolean = true,
    /** Триггер открытия экрана исходящего звонка. Вызывается из CallsScreen на «Новая встреча». */
    onStartCall: (com.example.template.feature.calls.CallContext) -> Unit = {},
) {
```

И в вызове `CallsScreen` (около строки 147 — `2 -> CallsScreen(viewModel = callsVm)`) поменять на:

```kotlin
2 -> CallsScreen(viewModel = callsVm, onStartCall = onStartCall)
```

- [ ] **Step 2: Добавить `onStartCall` в `CallsScreen` + пробросить в «Новая встреча»**

В `feature/calls/src/main/java/com/example/template/feature/calls/CallsScreen.kt`:

В сигнатуре `fun CallsScreen` добавить параметр:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallsScreen(
    viewModel: CallsViewModel,
    onStartCall: (CallContext) -> Unit = {},
) {
```

В теле функции (на уровне `Column`) добавить:

```kotlin
val brand = LocalAppBrand.current
val currentUser by viewModel.currentUserState().collectAsState()
val selfPersona = viewModel.personaForUser(currentUser.id)
```

Затем `CallActionTile("Новая встреча")` сейчас вызывает `onClick = {}`. Заменить:

```kotlin
CallActionTile(
    iconName = "video-call",
    label = "Новая встреча",
    onClick = {
        // Brand-meet — title из codename бренда, historyTarget = self.
        val brandCodename = com.example.template.BuildConfig.BRAND_CODENAME
        val brandTitle = brandCodename.replaceFirstChar { it.uppercase() } + " Meet"
        val target = HistoryTarget(
            id = currentUser.id,
            name = brandTitle,
            avatar = currentUser.avatar,
        )
        onStartCall(CallContext.BrandMeet(title = brandTitle, historyTarget = target))
    },
    modifier = Modifier.weight(1f),
)
```

NOTE: `viewModel.currentUserState()` и `viewModel.personaForUser()` — добавить в `CallsViewModel.kt` если их нет. Сейчас VM уже импортирует `repository`, добавляем:

```kotlin
fun currentUserState() = repository.currentUser
```

(в существующий VM, в начало или конец класса).

Поправить импорты в `CallsScreen.kt`:

```kotlin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.template.feature.calls.CallContext
import com.example.template.feature.calls.HistoryTarget
```

`brand` локально может не использоваться — если ругается на unused, убрать.

- [ ] **Step 3: Добавить `callContext` state и render OutgoingCallScreen в MainActivity**

В `MainActivity.kt` около строк 88-95 (где `openChatId`, `profileOpen`, `profileEditOpen`):

```kotlin
var callContext by remember {
    mutableStateOf<com.example.template.feature.calls.CallContext?>(null)
}
```

Найти `showBottomTabs = openChatId == null && !profileOpen`, заменить на:

```kotlin
val showBottomTabs = openChatId == null && !profileOpen && callContext == null
```

Найти вызов `MainScaffold(...)` (около строк 190+), добавить:

```kotlin
MainScaffold(
    onChatClick = onChatClick,
    onProfileClick = { profileOpen = true },
    showBottomTabs = showBottomTabs,
    enablePrewarm = splashGone,
    onStartCall = { ctx -> callContext = ctx },
)
```

После закрывающего `}` outer Box'а (где `SplashOverlay(...)`), добавить ПЕРЕД `SplashOverlay`:

```kotlin
val activeCallCtx = callContext
if (activeCallCtx != null) {
    com.example.template.feature.calls.OutgoingCallScreen(
        context = activeCallCtx,
        selfAvatar = container.repository.currentUser.value.avatar,
        repository = container.repository,
        onClose = { callContext = null },
    )
}
```

- [ ] **Step 4: Собрать**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Поставить и проверить вручную**

```bash
./gradlew :app:installDebug
```

Проверить: на вкладке «Звонки» тап на «Новая встреча» → открывается лобби с видео и заголовком «Foxtrot Meet» (для debug-сборки). Тап «Присоединиться» → in-call с таймером. End-call → закрытие + новая запись в списке Calls.

- [ ] **Step 6: Коммит**

```bash
git add app/src/main/java/com/example/template/MainScaffold.kt \
        app/src/main/java/com/example/template/MainActivity.kt \
        feature/calls/src/main/java/com/example/template/feature/calls/CallsScreen.kt \
        feature/calls/src/main/java/com/example/template/feature/calls/CallsViewModel.kt
git commit -m "feat(calls): wire OutgoingCallScreen через MainActivity для «Новая встреча»"
```

---

### Task 10: Wire ChatDetailScreen (P2P + Group)

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt`
- Modify: `app/src/main/java/com/example/template/MainActivity.kt` (передать `onStartCall` в `ChatDetailScreen`)

- [ ] **Step 1: Найти HeaderConfig.Chat.onCallClick в ChatDetailScreen**

```bash
grep -n "onCallClick" feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt
```

Около строки 275 — `onCallClick = { }`. Заменить на проксирование через новый параметр `onStartCall`.

- [ ] **Step 2: Добавить параметр в `ChatDetailScreen`**

В сигнатуре `fun ChatDetailScreen(...)`:

```kotlin
@Composable
fun ChatDetailScreen(
    viewModel: ChatDetailViewModel,
    // ... existing params ...
    onStartCall: (com.example.template.feature.calls.CallContext) -> Unit = {},
)
```

В теле функции там где формируется `HeaderConfig.Chat(...)` — обернуть в lambda:

```kotlin
val chat = viewModel.chat
val persona = if (chat != null && chat.type == com.example.template.core.model.ChatType.P2P) {
    chat.participantIds.firstOrNull()?.let { viewModel.personaById(it) }
} else null

val callContextFromChat = remember(chat, persona) {
    chat ?: return@remember null
    val target = if (chat.type == com.example.template.core.model.ChatType.P2P) {
        val p = persona ?: return@remember null
        com.example.template.feature.calls.HistoryTarget(
            id = chat.participantIds.first(),
            name = p.fullName,
            avatar = com.example.template.core.model.AvatarSpec(
                type = com.example.template.core.model.AvatarType.Person,
                initials = p.initials,
                gradientIndex = p.gradientIndex,
                imageAsset = p.avatarAsset,
            ),
        )
    } else {
        com.example.template.feature.calls.HistoryTarget(
            id = chat.id,
            name = chat.title,
            avatar = chat.avatar,
        )
    }
    when (chat.type) {
        com.example.template.core.model.ChatType.P2P ->
            com.example.template.feature.calls.CallContext.P2PCall(chat.id, persona?.fullName ?: chat.title, target)
        else ->
            com.example.template.feature.calls.CallContext.GroupCall(chat.id, chat.title, target)
    }
}

// ... в HeaderConfig.Chat:
onCallClick = { callContextFromChat?.let(onStartCall) },
```

NOTE: `viewModel.chat`/`viewModel.personaById` — если их нет на VM, добавить в `ChatDetailViewModel`:
```kotlin
val chat: Chat? get() = repository.getChat(chatId)
fun personaById(id: String) = repository.personaForUser(id)
```

- [ ] **Step 3: Передать `onStartCall` в `ChatDetailScreen` из `MainActivity`**

В `MainActivity.kt` найти вызов `ChatDetailScreen(...)`:

```kotlin
ChatDetailScreen(
    viewModel = visibleChatVm!!,
    onBack = closeChat,
    // ... existing ...
    onStartCall = { ctx -> callContext = ctx },
)
```

- [ ] **Step 4: Собрать**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Поставить и проверить вручную**

```bash
./gradlew :app:installDebug
```

Проверить:
- Открыть P2P-чат (например, Мария Климова) → тап на иконку звонка в хедере → открывается лобби с title = «Мария Климова».
- Присоединиться → таймер.
- End-call → закрытие. На вкладке Calls — новая запись Outgoing. В этом же чате — `Message.CallMeet` с длительностью.
- То же повторить для Group-чата.
- В P2P/Group тап на X в lobby → закрытие БЕЗ записи и без бабла.
- В in-call тап на Collapse (`fullscreen-exit`) → закрытие БЕЗ записи и без бабла.

- [ ] **Step 6: Коммит**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt \
        feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt \
        app/src/main/java/com/example/template/MainActivity.kt
git commit -m "feat(calls): wire ChatHeader.Call → OutgoingCallScreen (P2P + Group)"
```

---

### Task 11: Финальная проверка + sanity test full pass

**Files:** (никаких новых — только проверка)

- [ ] **Step 1: Запустить все unit-тесты**

```bash
./gradlew :core:data:testDebugUnitTest
```

Expected: PASS все тесты (включая новые `OutgoingCallRepositoryTest`).

- [ ] **Step 2: Чистая сборка**

```bash
./gradlew :app:clean :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` без warnings про дубликаты ресурсов или missing deps.

- [ ] **Step 3: Установить release-сборку для финальной проверки**

```bash
./gradlew :app:assembleRelease -Pbrand=foxtrot
adb install -r app/build/outputs/apk/release/app-release.apk
```

Проверить вживую сценарии:
1. Calls → «Новая встреча» → лобби «Foxtrot Meet» с self-аватаркой при camera OFF.
2. Join → in-call с реальным таймером MM:SS.
3. End-call → новая запись в Calls.
4. P2P-чат Header → лобби с именем собеседника.
5. End-call из P2P → запись в Calls + `CallMeet`-бабл в чате с durationMs.
6. Group-чат Header → лобби с названием группы.
7. End-call из Group → запись + бабл.
8. X в лобби — без записи.
9. Collapse в in-call — без записи.

- [ ] **Step 4: Финальный коммит (если потребовались правки)**

Если в процессе ручных проверок нашлись мелкие правки — закоммитить. Если всё ок:

```bash
git status   # должно быть clean
```

---

## Self-Review

Spec coverage:
- ✅ Entry points (3 триггера): Task 9 (NewMeeting), Task 10 (P2P/Group)
- ✅ State machine (Lobby/InCall, transitions): Task 4 (VM), Task 8 (Crossfade)
- ✅ CallContext data model: Task 2
- ✅ Архитектура файлов: всё перечислено в File Structure + создаётся в Tasks 2-8
- ✅ Видео-пайплайн (ExoPlayer, REPEAT_MODE_ALL, mute, RESIZE_MODE_ZOOM): Task 5 + Task 8
- ✅ Camera OFF → 120dp avatar overlay: Task 5 (`AvatarOverlay`)
- ✅ Визуал Lobby: Task 6
- ✅ Визуал InCall (5 кнопок, danger end-call): Task 7
- ✅ Таймер MM:SS: Task 4 (timerJob) + Task 7 (`formatElapsed`)
- ✅ Сохранение в Calls history (всегда): Task 3 (`recordOutgoingCall`) + Task 4 (`onEndCall`)
- ✅ `Message.CallMeet`-бабл для chat-triggered: Task 3 (`appendCallMeetMessage`) + Task 4
- ✅ Бренд / тема (accent + danger из brand): Task 6 + Task 7
- ✅ Hide BottomTabs во время звонка: Task 9 Step 3
- ✅ V1 Collapse = просто закрытие без записи: Task 4 (`onDismissWithoutRecording`)
- ✅ Тесты на builders: Task 3 Steps 1-4

Placeholder scan: нет TBD/TODO в выполняемых шагах. Есть `/* no-op V1 */` для flip/share/dots — это явное no-op, не недописанная часть.

Type consistency: 
- `CallContext` / `HistoryTarget` — определены в Task 2, используются в Tasks 4, 8, 9, 10.
- `CallToggles` / `CallPhase` — определены в Task 4, используются в Tasks 6, 7, 8.
- `recordOutgoingCall` / `appendCallMeetMessage` signature — определены в Task 3 interface, используются в Task 4 VM.
- `OutgoingCallScreen(context, selfAvatar, repository, onClose)` — определён в Task 8, вызывается в Task 9.
- Build helpers `buildOutgoingCallRecord` / `buildCallMeetMessage` — определены в Task 3.
