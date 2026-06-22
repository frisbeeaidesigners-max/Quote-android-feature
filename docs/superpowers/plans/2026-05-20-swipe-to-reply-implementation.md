# Swipe-to-reply Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реализовать вызов reply-режима свайпом влево по баблу сообщения, по аналогии с Telegram.

**Architecture:** Новый Compose-wrapper `SwipeToReplyItem` в `:core:ui/hosts/` ловит `detectHorizontalDragGestures`, двигает контент через `Modifier.graphicsLayer { translationX }`, рисует иконку-индикатор справа. На threshold даёт haptic, при release выше threshold вызывает `viewModel.startReply(messageId)`. VM получает новый public-метод `startReply`, общий с уже существующим reply-флоу через контекстное меню.

**Tech Stack:** Compose foundation gestures (`detectHorizontalDragGestures`, `Animatable`), `Modifier.graphicsLayer`, `LocalHapticFeedback`, существующая DSIcon-инфраструктура.

---

## File Structure

| Файл | Действие | Ответственность |
|---|---|---|
| `core/ui/src/main/java/com/example/template/core/ui/hosts/SwipeToReply.kt` | NEW | `SwipeToReplyItem` composable: gesture, drag state, иконка, haptic, threshold |
| `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt` | MODIFY | новый параметр `onSwipeReply`, оборачивание каждого item в `SwipeToReplyItem` |
| `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt` | MODIFY | новый public `startReply(messageId)`, рефактор `onMenuItem(REPLY)` чтобы тоже вызывал его |
| `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt` | MODIFY | пробрасывание `viewModel::startReply` в `MessageList(onSwipeReply = ...)` |

## Testing approach

В этой кодовой базе нет UI-тестов и нет VM-тестов с fake-репозиториями — единственный источник тестов для feature-кода это сборка `assembleRelease` + `adb install` + ручная проверка на устройстве `qgjrbqlrrkswk755`. Spec упоминал unit-тест на `startReply` — на практике он потребовал бы новой fake-repo infra, что несоразмерно для одной функции в 5 строк. Покрытие через manual smoke test в Task 6 с явным acceptance-list.

---

### Task 1: Вынести `startReply` из `onMenuItem` в публичный метод VM

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt`

- [ ] **Step 1: Открыть `ChatDetailViewModel.kt`, найти текущий `onMenuItem`** (около строки 84). Сейчас тело `Item.REPLY` встроено в `when`-блок.

- [ ] **Step 2: Добавить публичный метод `startReply` сразу после `onMenuItem`:**

```kotlin
fun startReply(messageId: String) {
    val original = _messages.value.firstOrNull { it.id == messageId } ?: return
    _replyContext.value = ReplyContext(
        originalId = original.id,
        authorName = resolveAuthorName(original),
        previewText = resolveMessagePreview(original),
    )
}
```

- [ ] **Step 3: В `onMenuItem` заменить тело case'а REPLY на вызов `startReply`:**

было:
```kotlin
ContextMenuView.Item.REPLY -> {
    val original = _messages.value.firstOrNull { it.id == st.messageId }
    if (original != null) {
        _replyContext.value = ReplyContext(
            originalId = original.id,
            authorName = resolveAuthorName(original),
            previewText = resolveMessagePreview(original),
        )
    }
}
```

стало:
```kotlin
ContextMenuView.Item.REPLY -> startReply(st.messageId)
```

- [ ] **Step 4: Commit (компиляция будет в Task 3-6 при общей сборке):**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt
git commit -m "refactor(chatdetail): extract startReply for reuse outside context menu"
```

---

### Task 2: Найти иконку reply и понять API DSIcon

**Files:**
- Read-only: `../icons-library/icons/`, `../android-components/components/src/main/java/com/example/components/contextmenu/`, `../android-components/components/src/main/java/com/example/components/designsystem/DSIcon.kt`, `../android-components/components/src/main/java/com/example/components/designsystem/DSIconPainter.kt`

- [ ] **Step 1: Посмотреть, какая иконка используется ContextMenuView для пункта REPLY** (чтобы взять то же имя и не плодить варианты):

```bash
grep -niE "reply|REPLY" ../android-components/components/src/main/java/com/example/components/contextmenu/ContextMenuView.kt | head -20
```

- [ ] **Step 2: Посмотреть наличие подходящих SVG в icons-library:**

```bash
ls ../icons-library/icons/ | grep -iE "reply|arrow|share"
```

- [ ] **Step 3: Зафиксировать выбор имени:**
  - Если ContextMenuView использует `DSIcon.named("reply", ...)` — берём `"reply"`.
  - Иначе берём то имя, что фактически есть в `icons-library/icons/` и ближе всего по смыслу (`arrow-uturn-left`, `arrow-curve-left`, `corner-up-left`).
  - Зафиксировать выбранное имя как константу `ICON_NAME` в коде Task 3.

- [ ] **Step 4: Узнать API DSIcon для Compose-использования.** В моём предложенном коде Task 3 я использую `androidx.compose.foundation.Image(painter = ..., ...)`. Это требует `Painter`. Открыть `DSIcon.kt` и `DSIconPainter.kt`:

```bash
grep -n "fun named\|fun namedPainter\|^class\|^object" ../android-components/components/src/main/java/com/example/components/designsystem/DSIcon.kt ../android-components/components/src/main/java/com/example/components/designsystem/DSIconPainter.kt
```

  - Если `DSIcon.named` возвращает `Drawable` — использовать `DSIconPainter.named(...)` или эквивалент, который возвращает `Painter`.
  - Если возвращает `Painter` напрямую — оставить как в Task 3.
  - Сигнатура вызова в Task 3 (`iconPainter = DSIcon.named(context, ICON_NAME, tintColor = ...)`) **корректировать здесь же** на ту, что реально есть в коде. Без этого Task 3 не скомпилируется.

- [ ] **Step 5: Нет коммита** — исследовательский шаг, результат уйдёт в код Task 3.

---

### Task 3: Создать `SwipeToReplyItem` composable

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/hosts/SwipeToReply.kt`

- [ ] **Step 1: Создать файл со следующим содержимым.** Имя `ICON_NAME` и точный вызов `DSIcon.named(...) / DSIconPainter.named(...)` подставить из результатов Task 2.

```kotlin
package com.example.template.core.ui.hosts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.components.designsystem.DSIcon
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import kotlin.math.abs
import kotlinx.coroutines.launch

private val THRESHOLD_DP = 60.dp
private const val ICON_NAME = "reply" // ← подставить из Task 2 если другое имя
private val ICON_SIZE = 24.dp
private const val ICON_RESISTANCE = 0.5f

/**
 * Compose-обёртка, добавляющая жест "свайп влево → ответить" к row item'у MessageList'а.
 * При движении пальца влево содержимое (content) сдвигается через graphicsLayer.translationX,
 * справа поверх рисуется иконка ответа, прозрачность которой растёт пропорционально смещению.
 *
 * При пересечении threshold (60dp от старта) даёт лёгкий haptic и scale-pulse иконки.
 * На release: если |offset| >= threshold — вызывает onTriggerReply(messageId), иначе просто
 * spring back. На release всегда возвращает offset к 0 через Animatable.
 *
 * Если [enabled] = false (например для Voice/Link/CallMeet типов), gesture не вешается,
 * content рендерится как есть.
 */
@Composable
fun SwipeToReplyItem(
    messageId: String,
    enabled: Boolean,
    onTriggerReply: (messageId: String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val thresholdPx = with(density) { THRESHOLD_DP.toPx() }
    val dragOffset = remember(messageId) { Animatable(0f) }
    val iconScale = remember(messageId) { Animatable(1f) }
    var didCrossThreshold by remember(messageId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current

    // brand-зависимый цвет иконки. Если DSIcon.named вернул null (нет SVG в синканных
    // assets), рисуем пустой Box того же размера: визуально иконка отсутствует, но gesture
    // всё равно работает — пользователь увидит как бабл уезжает влево.
    val iconPainter = DSIcon.named(
        context = context,
        name = ICON_NAME,
        tintColor = Color(brand.accentColor(isDark)),
    )

    Box(
        modifier = modifier.pointerInput(messageId) {
            detectHorizontalDragGestures(
                onDragStart = {
                    didCrossThreshold = false
                },
                onDragEnd = {
                    val crossed = abs(dragOffset.value) >= thresholdPx
                    scope.launch { dragOffset.animateTo(0f, spring()) }
                    if (crossed) onTriggerReply(messageId)
                },
                onDragCancel = {
                    scope.launch { dragOffset.animateTo(0f, spring()) }
                },
            ) { change, dragAmount ->
                change.consume()
                val newOffset = (dragOffset.value + dragAmount).coerceAtMost(0f)
                scope.launch { dragOffset.snapTo(newOffset) }
                if (!didCrossThreshold && abs(newOffset) >= thresholdPx) {
                    didCrossThreshold = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        iconScale.animateTo(1.15f, tween(75))
                        iconScale.animateTo(1.0f, tween(75))
                    }
                }
            }
        },
    ) {
        // Иконка справа от content'а. Видна только пока content уезжает влево
        // (alpha считается от |offset| / threshold).
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(ICON_SIZE)
                .graphicsLayer {
                    val absOffset = abs(dragOffset.value)
                    alpha = (absOffset / thresholdPx).coerceIn(0f, 1f)
                    translationX = dragOffset.value * ICON_RESISTANCE
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                },
        ) {
            iconPainter?.let { painter ->
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SIZE),
                )
            }
        }

        // Content поверх иконки, сдвигается влево вместе с пальцем.
        Box(
            modifier = Modifier.graphicsLayer {
                translationX = dragOffset.value
            },
        ) {
            content()
        }
    }
}
```

- [ ] **Step 2: Smoke compile:**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
& .\gradlew.bat :core:ui:compileReleaseKotlin
```

Expected: `BUILD SUCCESSFUL` без ошибок компиляции в `SwipeToReply.kt`. Если есть ошибка про `DSIcon.named` сигнатуру — вернуться к Task 2 и поправить.

- [ ] **Step 3: Commit:**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/SwipeToReply.kt
git commit -m "feat(core-ui): add SwipeToReplyItem compose wrapper"
```

---

### Task 4: Подключить `SwipeToReplyItem` в `MessageList`

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt`

- [ ] **Step 1: Добавить параметр `onSwipeReply` в сигнатуру `MessageList`** (около строки 127), последним:

было:
```kotlin
@Composable
fun MessageList(
    messages: List<Message>,
    modifier: Modifier = Modifier,
    personaByUserId: (String) -> Persona? = { null },
    onBubbleTap: (messageId: String) -> Unit = {},
    onReactionTap: (messageId: String, emoji: String) -> Unit = { _, _ -> },
    onAddReactionTap: (messageId: String) -> Unit = {},
) {
```

стало:
```kotlin
@Composable
fun MessageList(
    messages: List<Message>,
    modifier: Modifier = Modifier,
    personaByUserId: (String) -> Persona? = { null },
    onBubbleTap: (messageId: String) -> Unit = {},
    onReactionTap: (messageId: String, emoji: String) -> Unit = { _, _ -> },
    onAddReactionTap: (messageId: String) -> Unit = {},
    onSwipeReply: (messageId: String) -> Unit = {},
) {
```

- [ ] **Step 2: Обернуть содержимое `items(reversed, key = { it.id }) { msg -> ... }` в `SwipeToReplyItem`.** Внутри `items { msg -> }` оставить все вычисления (persona, senderName, replySender и т.д.), а сам `Column(...)` со всем `when (msg) { ... }` внутри — перенести в content-слот `SwipeToReplyItem`:

было (упрощённо):
```kotlin
items(reversed, key = { it.id }) { msg ->
    val position = ...
    val topPadding = ...
    // ...все остальные val...
    val replySender = msg.replyTo?.authorName?.replace(' ', ' ')
    val replyText = resolveReplyText(msg, messagesById)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onBubbleTap(msg.id) },
    ) {
        when (msg) { /* все типы баблов */ }
    }
}
```

стало:
```kotlin
items(reversed, key = { it.id }) { msg ->
    val position = ...
    val topPadding = ...
    // ...все остальные val оставить как есть...
    val replySender = msg.replyTo?.authorName?.replace(' ', ' ')
    val replyText = resolveReplyText(msg, messagesById)
    SwipeToReplyItem(
        messageId = msg.id,
        enabled = msg is Message.Text || msg is Message.Media,
        onTriggerReply = onSwipeReply,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onBubbleTap(msg.id) },
        ) {
            when (msg) { /* все типы баблов — БЕЗ ИЗМЕНЕНИЙ */ }
        }
    }
}
```

**Никакие val перед SwipeToReplyItem не трогать**, и `when (msg) { ... }` внутри Column тоже не переписывать — только обернуть.

- [ ] **Step 3: Smoke compile:**

```powershell
& .\gradlew.bat :core:ui:compileReleaseKotlin
```

Expected: `BUILD SUCCESSFUL`. Если ругается на `Message.Text`/`Message.Media` — импорт должен уже быть в MessageList.kt, иначе добавить.

- [ ] **Step 4: Commit:**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt
git commit -m "feat(messagelist): wrap items in SwipeToReplyItem with onSwipeReply callback"
```

---

### Task 5: Пробросить callback из `ChatDetailScreen` в `MessageList`

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt`

- [ ] **Step 1: Найти вызов `MessageList(...)` (~строка 176)** и добавить `onSwipeReply` последним аргументом:

было:
```kotlin
MessageList(
    messages = messages,
    modifier = Modifier.fillMaxSize(),
    personaByUserId = viewModel::personaForUser,
    onBubbleTap = { id ->
        panelRef.value?.dismissKeyboard()
        viewModel.openContextMenu(id)
    },
    onReactionTap = viewModel::toggleReactionDirect,
    onAddReactionTap = { id ->
        panelRef.value?.dismissKeyboard()
        viewModel.openContextMenu(id)
    },
)
```

стало:
```kotlin
MessageList(
    messages = messages,
    modifier = Modifier.fillMaxSize(),
    personaByUserId = viewModel::personaForUser,
    onBubbleTap = { id ->
        panelRef.value?.dismissKeyboard()
        viewModel.openContextMenu(id)
    },
    onReactionTap = viewModel::toggleReactionDirect,
    onAddReactionTap = { id ->
        panelRef.value?.dismissKeyboard()
        viewModel.openContextMenu(id)
    },
    onSwipeReply = viewModel::startReply,
)
```

- [ ] **Step 2: Smoke compile:**

```powershell
& .\gradlew.bat :feature:chatdetail:compileReleaseKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit:**

```bash
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt
git commit -m "feat(chatdetail): wire viewModel::startReply into MessageList.onSwipeReply"
```

---

### Task 6: Сборка, установка, ручная проверка на устройстве

**Files:**
- None (только smoke test)

- [ ] **Step 1: Release-сборка foxtrot:**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
& .\gradlew.bat :app:assembleRelease -Pbrand=foxtrot
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Установка на устройство:**

```powershell
& "C:\Users\Grond\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "C:\Claude\test_project\android-template\app\build\outputs\apk\release\app-release.apk"
```

Expected: `Success`.

- [ ] **Step 3: Ручная проверка по acceptance-list'у:**

   1. Открыть P2P-чат с историей. **Свайпнуть влево на SOMEONE-баббле (Text-сообщение).** Бабл (и весь row с реакциями/sender-name) уезжает влево, справа появляется иконка-стрелка. После ~60dp — лёгкий haptic-tick, иконка коротко пульсирует. Отпустить — Column возвращается обратно, **MessagePanel снизу показывает reply-блок** (имя автора + первая строчка сообщения), IME поднимается с фокусом в EditText.

   2. **То же со своим (MY) Text-сообщением** — должно работать симметрично.

   3. **То же с Media-сообщением** (с подписью и без). Reply-блок показывает `caption` или `Фото`/`Видео`.

   4. **На Voice/Link/CallMeet баббле** — свайп не должен работать (`enabled = false`). Палец двигается влево — ничего не происходит, никакой иконки.

   5. **Tap по баббле** (как раньше) открывает контекстное меню — не сломалось. И «Ответить» из контекстного меню тоже работает (это валидация рефакторинга `startReply` в Task 1).

   6. **Свайп под углом / преимущественно вертикальный**: должен скроллиться список, а не дёргаться бабл. Compose touch-slop разруливает.

   7. **Свайп вправо** или **короткий свайп (< 60dp)** — Column springs back, никакого reply.

   8. **Свайпнуть подряд два разных сообщения** — replyContext перезапишется на второе. Нормально.

   9. Если что-то из 1–8 не работает корректно — сделать `git commit -m "wip: swipe-to-reply"` с текущим состоянием и зарепортить, чтобы вернуться к фиксу не теряя прогресса.

- [ ] **Step 4: Если всё ок** — финального коммита нет (все коммиты по ходу). Можно опционально обновить `project_reply.md` в memory упомянув свайп — но это вне scope этого плана.

---

## Спасательные шаги при типовых проблемах

- **Иконка не появляется** (alpha-fade видно через прозрачный Box, но контент не рисуется).
  В Task 2 проверь имя файла в `../icons-library/icons/`. Если `reply.svg` нет — поправь константу `ICON_NAME` в `SwipeToReply.kt`. Запусти `./gradlew :app:syncIconsFromLibrary` для синка в app-assets — без него `DSIcon.named` вернёт null. После полной сборки в Task 6 этот sync вызывается из `preBuild`, так что отдельно делать не нужно, но при isolated `:core:ui:compileReleaseKotlin` — sync не выполняется (он живёт в `:app`).

- **Жест не отлавливается, бабл не двигается.**
  Проверь, что `pointerInput(messageId)` стоит на ВНЕШНЕМ Box (modifier = modifier), а не на content'е. Если внутренний AndroidView consume'ит ACTION_DOWN (теоретически возможно для какого-то баббла с custom touch-handling) — добавить в content `Modifier.pointerInteropFilter { false }` для пропуска тачей наверх. Для текущих BubblesView это маловероятно: они не overrid'ят onTouchEvent для horizontal drag.

- **Вертикальный скролл "застревает"** при попытке скроллнуть в области сообщений.
  `detectHorizontalDragGestures` должен сам передавать вертикальные жесты вверх через slop. Если по факту не работает — заменить на `awaitEachGesture { ... }` с ручным определением axis (через первое движение DOWN→MOVE).

- **Reply-блок не появляется при release выше threshold.**
  Logcat должен показывать вызов `viewModel.startReply(messageId)`. Если вызывается, но `_replyContext` не меняется — debug в `_messages.value.firstOrNull { it.id == messageId }` (возможен mismatch ID, хотя при текущей архитектуре маловероятно).

- **Двойной haptic за один жест.**
  Убедись, что `didCrossThreshold` сбрасывается в `onDragStart` и проверяется через `if (!didCrossThreshold && abs(newOffset) >= thresholdPx)` (а не отдельно).

- **Анимация подёргивается / иконка скачет.**
  Все state-mutations внутри `onHorizontalDrag` обёрнуты в `scope.launch { dragOffset.snapTo(...) }` — `Animatable.snapTo` это suspend-функция и должна вызываться из coroutine. Если по ошибке вызвана синхронно — Kotlin compiler даст ошибку, но если вдруг прошла через какой-то trick — будет race.
