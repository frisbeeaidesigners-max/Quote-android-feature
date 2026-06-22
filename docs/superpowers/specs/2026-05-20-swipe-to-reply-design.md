# Swipe-to-reply на сообщения

**Дата:** 2026-05-20
**Статус:** design

## Цель

Дать пользователю возможность вызвать reply-режим (тот же, что открывается из контекстного меню → «Ответить») жестом — свайпом влево по баблу сообщения. По образцу Telegram.

## Scope

- Работает на типах **Text** и **Media**. На Voice/Link/CallMeet gesture не вешается вообще (нет визуального ответа на drag, нет триггера) — те же типы, на которых сейчас не открывается контекстное меню. Если в будущем расширим reply на эти типы, расширим и swipe.
- Работает одинаково на **MY** и **SOMEONE** бабблах.
- Только свайп **влево**. Свайп вправо или вертикальный жест не обрабатываются нашим handler'ом и уходят в `LazyColumn` (вертикальный скролл) либо игнорируются.

## UX

1. Палец начинает горизонтальное движение по row item'у сообщения. Compose touch-slop разруливает «горизонталь / вертикаль» — если жест преимущественно вертикальный, control отдаётся `LazyColumn` без потерь.
2. **Весь row item** (Column, содержащий бабл + sender-name + avatar + строку реакций) двигается влево вместе с пальцем через `Modifier.graphicsLayer { translationX = dragOffset }`. Палец сдвинул на N — Column сдвинулся на N.
3. **Иконка reply** появляется справа за пределами Column'а, в `Box(align = CenterEnd)` overlay:
   - alpha = `(|dragOffset| / THRESHOLD).coerceIn(0f, 1f)` — нарастает линейно от 0 до 1 пока тянем до threshold.
   - translateX = `dragOffset * 0.5f` — двигается медленнее пальца (resistance 0.5×), создавая «выезжающий» эффект.
4. **Threshold = 60dp.** При первом пересечении threshold внутрь жеста (т.е. `|dragOffset|` стал ≥ THRESHOLD):
   - срабатывает `LocalHapticFeedback.performHapticFeedback(LongPress)` — один раз за жест.
   - иконка делает scale-pulse 1.0 → 1.15 → 1.0 за ~150мс.
   - Флаг `didCrossThreshold` блокирует повторный haptic, если пользователь дёргает туда-сюда в районе threshold.
5. **Release:**
   - `|dragOffset| < THRESHOLD` — `Animatable.animateTo(0f, spring())` — Column возвращается обратно, никакого триггера.
   - `|dragOffset| >= THRESHOLD` — Animatable анимирует обратно к 0 + параллельно вызывается `viewModel.startReply(messageId)`. В `ChatDetailScreen` уже есть `LaunchedEffect(panel, replyCtx?.originalId)`, который при появлении нового `replyCtx` фокусит EditText и поднимает IME — это поведение переиспользуется без изменений.
6. **Гран. кейсы:**
   - `onDragCancel` (палец ушёл со screen без обычного release) — spring back to 0, без триггера.
   - Перерываемый drag (новый touch во время animateTo back) — Animatable обнуляет animation и снова даёт snapTo. Поведение естественное.
   - Перетянули далеко вправо (положительный offset). Игнорируем: `dragOffset.value` клампится в `[-∞, 0]` через `coerceAtMost(0f)`.

## Архитектура

### `core/ui/.../hosts/MessageList.kt`

Каждый item получает обёртку:

```kotlin
items(reversed, key = { it.id }) { msg ->
    val canReply = msg is Message.Text || msg is Message.Media
    if (canReply) {
        SwipeToReplyItem(
            messageId = msg.id,
            onTriggerReply = onSwipeReply,
        ) {
            // существующий Column с баблом, реакциями и т.д.
        }
    } else {
        // существующий Column без обёртки
    }
}
```

`SwipeToReplyItem` (новый private @Composable в том же файле или соседнем `SwipeToReply.kt`):

- Держит `Animatable<Float>` на dragOffset (remember per messageId).
- Держит `var didCrossThreshold by remember { mutableStateOf(false) }`.
- Внешний `Box(modifier.pointerInput(messageId) { detectHorizontalDragGestures(...) })` ловит жест.
- Внутри Box — два слоя:
  - **Иконка reply** в `Box(align = CenterEnd)`. Видимость / alpha / translateX / scale считаются как функция `dragOffset.value`.
  - **Content slot** (тот самый Column) с `Modifier.graphicsLayer { translationX = dragOffset.value }`.
- `detectHorizontalDragGestures`:
  - `onDragStart` — `didCrossThreshold = false`.
  - `onHorizontalDrag` — `change.consume()`; `dragOffset.snapTo((dragOffset.value + delta).coerceAtMost(0f))`; если перешли threshold впервые — haptic + iconScalePulse.start().
  - `onDragEnd` — если `|dragOffset.value| >= THRESHOLD_PX` → `onTriggerReply(messageId)` + animate to 0; иначе только animate to 0.
  - `onDragCancel` — animate to 0.

### `core/ui/.../hosts/MessageList.kt` — сигнатура

```kotlin
@Composable
fun MessageList(
    messages: List<Message>,
    ...
    onSwipeReply: (messageId: String) -> Unit = {},  // новое
)
```

### `feature/chatdetail/.../ChatDetailScreen.kt`

```kotlin
MessageList(
    ...
    onSwipeReply = viewModel::startReply,
)
```

### `feature/chatdetail/.../ChatDetailViewModel.kt`

Существующий `onMenuItem(...)` для `Item.REPLY` уже делает всё что нужно. Выделим общий helper:

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

И в `onMenuItem(Item.REPLY, ...)` вместо inline-кода вызвать `startReply(st.messageId)` плюс `dismissContextMenu()`. Уменьшает дублирование.

## Конфликты и их разрешение

- **С вертикальным скроллом `LazyColumn`.** `detectHorizontalDragGestures` использует touch-slop: если перед началом drag'а пользователь успевает дёрнуть пальцем вертикально, Compose отдаёт жест LazyColumn'у и наш handler не активируется. Никаких ручных guards не нужно.
- **С тапом по баблу (`Column.clickable { onBubbleTap }`, открывает контекстное меню).** Эти два mechanism'а живут на разных уровнях обработки. `.clickable` срабатывает только если жест завершился без drag за пределы slop. Как только drag начинается — clickable cancel'ится. Никаких ручных guards не нужно.
- **С AndroidView внутри Column (BubblesView и т.д.).** AndroidView'ы баблов не обрабатывают horizontal drag — это custom-drawn View с onDraw, без onTouchEvent для жестов. Тачи проходят через них в Compose layer. Если в будущем какой-то AndroidView начнёт consume drag — придётся либо modify (no!) либо использовать `Modifier.pointerInteropFilter`. На текущей кодовой базе проблемы нет.
- **С тачами на реакциях (`onReactionTap`, кнопка «+» reactions).** Тапы по конкретным children Compose-layer'а потребляют свои тачи раньше; horizontal drag detection активируется только на DOWN+slop вне consumed-области. Конфликта нет — но в реализации проверим вручную.

## Параметры

| Имя | Значение | Где задаётся |
|---|---|---|
| `THRESHOLD_DP` | 60dp | constant в `SwipeToReply.kt` |
| Resistance иконки | 0.5× от dragOffset | константа в той же функции |
| Haptic type | `HapticFeedbackType.LongPress` | при пересечении threshold |
| Icon scale pulse | 1.0 → 1.15 → 1.0 за ~150мс | `Animatable<Float>` separate |
| Spring back animation | дефолтный `spring()` через Animatable | `onDragEnd` / `onDragCancel` |
| Иконка | `DSIcon.named(context, "reply", ...)` или ближайший аналог из icons-library; проверяется на этапе реализации, fallback — статичная Compose-стрелка | `SwipeToReplyItem` |

## Что НЕ делаем (out of scope)

- Свайп вправо для каких-либо действий.
- Свайп для других action'ов (pin, forward, delete).
- Reply на Voice/Link/CallMeet — это отдельная задача (расширение `openContextMenu` + соответствующие тесты).
- Анимация content'а внутри иконки (mask reveal, fancy easing) — берём базовый scale-pulse.
- Memoization / performance optimizations для огромных списков — типичная история на 50–500 сообщений уже работает плавно через существующую инфраструктуру.

## Тестирование

- Manual: открыть P2P-чат с Text-сообщениями, свайпнуть влево MY-бабл и SOMEONE-бабл, убедиться что reply-блок появляется в MessagePanel, IME поднимается, имя автора и preview корректны.
- Manual: то же с Media (с подписью и без) — preview = caption или «Фото»/«Видео».
- Manual: на Voice-бабле — жест ничего не делает.
- Manual: не должен ломаться вертикальный скролл при «случайных» косых свайпах.
- Manual: tap по баблу по-прежнему открывает контекстное меню (drag != tap).
- Unit-тесты на новый `ChatDetailViewModel.startReply(messageId)`: проверка что `replyContext` устанавливается с правильными `originalId` / `authorName` / `previewText`.

## Открытые вопросы

Нет.
