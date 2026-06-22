# Системные сообщения и sticky-дата

**Дата:** 2026-05-21
**Статус:** design

## Цель

Ввести в чат-ленту понятие «системные сообщения» — короткие centered-pill chips, разделяющие переписку. Покрывает два визуально одинаковых, но разных по происхождению типа:

1. **Date-разделители** — выводятся из timestamp'ов сообщений (синтетические, нет в `Message`-модели). Дополнительно для них есть **sticky-поведение**: при скролле наверху ленты висит chip с датой текущей видимой области; после 2 секунд idle — фейдится. По аналогии с Telegram.
2. **Inline-системные сообщения с данными** — данные в модели (`Message.System`). Первый и пока единственный подтип: **Joined** («Мария Климова присоединилась к группе»).

Оба типа подчиняются общему правилу группировки: 4dp между подряд идущими system-row'ами, 8dp между группой system и соседними бабблами / другой группой системных.

## Scope

**В сприте:**
- Новый тип `Message.System(kind=Joined)` в `core/model`.
- Поле `gender: Gender` в `Persona` (Male/Female) для корректного суффикса глагола.
- Unified row model в `MessageList.kt`: sealed `RowItem { Bubble, SystemRow }` + flatten-функция.
- Алгоритм группировки 4dp/8dp.
- Sticky date overlay поверх `LazyColumn`, fade через 2с idle.
- 2-3 мок-примера `Message.System(kind=Joined)` в group-чатах.

**Вне сприта:**
- Подтипы `Left`, `GroupRenamed`, `GroupCreated`, `PinnedMessage` и пр. (YAGNI — добавим, когда понадобятся; модель готова).
- Полноценный DS-компонент в `android-components` (пользователь готовит, заменим вызов когда будет готов).
- P2P-чаты — system messages туда не добавляем (Joined в P2P не имеет смысла).

## Архитектура

### 1. Data model (`core/model`)

**`Message.kt`** — добавляем sealed-вариант:

```kotlin
@Serializable @SerialName("system")
data class System(
    override val id: String,
    override val chatId: String,
    override val senderId: String,
    override val timestamp: Long,
    override val isMine: Boolean = false,
    override val status: MessageStatus = MessageStatus.NONE,
    override val reactions: List<Reaction> = emptyList(),
    override val replyTo: ReplyPreview? = null,
    val kind: SystemKind,
) : Message()

@Serializable
enum class SystemKind { Joined }
```

- `senderId` — u-id участника, к кому относится событие (для Joined — кто присоединился).
- `isMine`/`status`/`reactions`/`replyTo` — формально присутствуют (требуются абстрактными полями родителя), но игнорируются на рендере. Дефолты пустые/нейтральные.
- JSON-полиморфизм работает через тот же `classDiscriminator = "type"` в `JsonModule.AppJson`, тэг `"system"` — никаких правок serializer'а не требуется.

**`Persona.kt`** — добавляем поле:

```kotlin
@Serializable
enum class Gender { Male, Female }

@Serializable
data class Persona(
    val id: String,
    val firstName: String,
    val lastName: String,
    val avatarAsset: String?,
    val gradientIndex: Int,
    val gender: Gender,         // ← new
)
```

Поле обязательное (без дефолта). В `personas.json` проставляем для всех 14 записей.

**Mock data:**
- В `personas.json` — gender каждой персоны (по имени).
- В 2-3 group-чатах (`grp-01`, `grp-05`, ... — конкретные выберем в impl plan) добавить файл `messages/grp-NN.json` с парой обычных bubble + одним Message.System (kind=Joined). Конкретный текст рендерится UI, не приходит из JSON.

### 2. UI row model (`core/ui/.../MessageList.kt`)

Приватные типы внутри файла:

```kotlin
private sealed class RowItem {
    abstract val key: String
    abstract val timestamp: Long
    
    data class Bubble(val message: Message) : RowItem() {
        override val key get() = message.id
        override val timestamp get() = message.timestamp
    }
    
    data class SystemRow(
        val text: String,
        override val timestamp: Long,
        override val key: String,
        val isDate: Boolean,
    ) : RowItem()
}

private data class FlattenedRows(
    val rows: List<RowItem>,
    val dateForRow: Map<String, String>,  // rowKey → applicable date label
)
```

**Flatten-функция (chronological iteration):**

```kotlin
private fun flattenToRows(
    messages: List<Message>,
    now: Long,
    personaByUserId: (String) -> Persona?,
): FlattenedRows {
    val rows = mutableListOf<RowItem>()
    val dateForRow = HashMap<String, String>()
    var prevDayKey: Int? = null
    var currentDateLabel: String? = null
    val cal = Calendar.getInstance()
    
    for (msg in messages) {
        cal.timeInMillis = msg.timestamp
        val dayKey = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        if (dayKey != prevDayKey) {
            currentDateLabel = TimeFormatter.formatDateSeparator(msg.timestamp, now)
            val dateRow = RowItem.SystemRow(
                text = currentDateLabel,
                timestamp = msg.timestamp,
                key = "date-$dayKey",
                isDate = true,
            )
            rows.add(dateRow)
            dateForRow[dateRow.key] = currentDateLabel
            prevDayKey = dayKey
        }
        val row = when (msg) {
            is Message.System -> RowItem.SystemRow(
                text = renderSystemText(msg, personaByUserId),
                timestamp = msg.timestamp,
                key = "sys-${msg.id}",
                isDate = false,
            )
            else -> RowItem.Bubble(msg)
        }
        rows.add(row)
        dateForRow[row.key] = currentDateLabel ?: ""
    }
    return FlattenedRows(rows, dateForRow)
}

private fun renderSystemText(
    msg: Message.System,
    personaByUserId: (String) -> Persona?,
): String {
    val p = personaByUserId(msg.senderId)
    val name = p?.let { "${it.firstName} ${it.lastName}" } ?: "Кто-то"
    return when (msg.kind) {
        SystemKind.Joined -> {
            val verb = if (p?.gender == Gender.Female) "присоединилась" else "присоединился"
            "$name $verb к группе"
        }
    }
}
```

Все computations кэшируются через `remember(messages, now, personaByUserId)`.

### 3. Grouping algorithm

**Top padding над row[i]** (chronological order):

| prev | cur | padding |
|---|---|---|
| — (i=0) | * | 0dp |
| SystemRow | SystemRow | 4dp |
| SystemRow | Bubble | 8dp |
| Bubble | SystemRow | 8dp |
| Bubble | Bubble (joins prev по same-sender + 5min) | 4dp |
| Bubble | Bubble (новая bubble-группа) | 8dp |

Precomputed как `Map<rowKey, Dp>` через `remember(rows, groupPositions)`.

**Same-sender bubble grouping — SystemRow breaker:**

```kotlin
private fun computeGroupPositions(rows: List<RowItem>): Map<String, GroupPosition> {
    val result = HashMap<String, GroupPosition>()
    for (i in rows.indices) {
        val cur = rows[i] as? RowItem.Bubble ?: continue
        var prevMessage: Message? = null
        for (j in i - 1 downTo 0) {
            when (val r = rows[j]) {
                is RowItem.SystemRow -> { prevMessage = null; break }
                is RowItem.Bubble -> { prevMessage = r.message; break }
            }
        }
        var nextMessage: Message? = null
        for (j in i + 1 until rows.size) {
            when (val r = rows[j]) {
                is RowItem.SystemRow -> { nextMessage = null; break }
                is RowItem.Bubble -> { nextMessage = r.message; break }
            }
        }
        val joinsPrev = prevMessage != null && sameGroup(prevMessage, cur.message)
        val joinsNext = nextMessage != null && sameGroup(cur.message, nextMessage)
        result[cur.message.id] = when {
            !joinsPrev && !joinsNext -> GroupPosition.SINGLE
            !joinsPrev && joinsNext -> GroupPosition.FIRST
            joinsPrev && joinsNext -> GroupPosition.MIDDLE
            else -> GroupPosition.LAST
        }
    }
    return result
}
```

Два same-sender bubble'а с system-row между ними рендерятся как два независимых баббла: полные углы 14dp, sender name + avatar на каждом (для Group/Channel).

### 4. Rendering

**Единый Composable для всех system-row'ов** — заменяет существующий TEMP `DateSeparator`:

```kotlin
// ВРЕМЕННОЕ РЕШЕНИЕ. Плейсхолдер до прихода канонического DS-компонента из
// android-components. Centered semi-transparent pill, белый текст,
// DSTypography.subhead4M. Никаких внутренних vertical padding — top-зазор
// контролируется caller'ом из topPaddingPerRow.
@Composable
private fun SystemMessageRow(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.30f))
            .padding(horizontal = 10.dp, vertical = 3.dp)) {
            Text(text, color = Color.White, style = DSTypography.subhead4M.toComposeTextStyle())
        }
    }
}
```

**items() блок:**

```kotlin
items(reversed, key = { it.key }) { row ->
    val topPad = topPaddingPerRow[row.key] ?: 0.dp
    when (row) {
        is RowItem.SystemRow -> SystemMessageRow(
            text = row.text,
            modifier = Modifier.padding(top = topPad),
        )
        is RowItem.Bubble -> {
            // существующий код рендера баббла (persona, senderName, senderAvatar,
            // SwipeToReplyItem, реакции и т.д.). ЕДИНСТВЕННОЕ изменение —
            // padding(top = topPadding) → padding(top = topPad) из unified map.
            // Локальный расчёт topPadding из GroupPosition удаляется.
        }
    }
}
```

**Верхний уровень MessageList:**

```kotlin
Box(modifier = modifier) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(reversed, key = { it.key }) { row -> ... }
    }
    StickyDateOverlay(
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        listState = listState,
        dateForRow = dateForRow,
    )
}
```

`latestId`-ключ авто-скролла остаётся `messages.lastOrNull()?.id` (не RowItem.key) — чтобы добавление синтетических date-row'ов не дёргало animateScrollToItem.

### 5. Sticky date overlay

```kotlin
@Composable
private fun StickyDateOverlay(
    modifier: Modifier,
    listState: LazyListState,
    dateForRow: Map<String, String>,
) {
    val topVisibleKey by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.key as? String
        }
    }
    val isTopRowDate = topVisibleKey?.startsWith("date-") == true
    val dateLabel = topVisibleKey?.let { dateForRow[it] }
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            visible = true
        } else {
            delay(2000)
            visible = false
        }
    }
    
    val showSticky = visible && !isTopRowDate && !dateLabel.isNullOrEmpty()
    
    AnimatedVisibility(
        visible = showSticky,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(300)),
        modifier = modifier,
    ) {
        SystemMessageRow(text = dateLabel ?: "")
    }
}
```

**Поведение:**
- С `reverseLayout=true` визуально верхний item = `visibleItemsInfo.last()` (наибольший list-index).
- `derivedStateOf` — overlay рекомпозируется только при смене ключа верхнего видимого row'а, не на каждый scroll-pixel.
- `LaunchedEffect(isScrollInProgress)` — на старт скролла `visible = true`. На остановку — `delay(2000)`, потом `visible = false`. Любой новый скролл во время delay → effect рестартуется, delay отменяется.
- `isTopRowDate` — когда сам inline-DateRow занимает sticky-позицию, overlay скрыт. Так достигается визуальная непрерывность «прилипания»: inline остаётся в позиции 8dp от хедера → off-screen → overlay принимает эстафету с той же датой.
- Fade: in 150ms (responsive), out 300ms (плавный после idle).
- Холодный старт чата (open chat): `isScrollInProgress=false`, `visible` стартует с `false`. Sticky НЕ показывается, дата видна через inline-row на самом верху ленты — пользователь не теряет контекст.
- Пустая лента: `dateLabel=null`, sticky никогда не показывается.

**Top-content overlap:** при показанном sticky overlay лежит поверх верхнего видимого баббла. Намеренно НЕ резервируем `contentPadding.top` — overlap кратковременен (только во время активного скролла), Telegram делает аналогично.

## Тесты

Юнит-тесты в `core/data` (или ближайшем подходящем модуле):

- **`FlattenToRowsTest`:**
  - Пустой список messages → пустой rows.
  - Одно сообщение → `[DateRow, Bubble]`.
  - Два сообщения одного дня → `[DateRow, Bubble, Bubble]`.
  - Два сообщения разных дней → `[DateRow1, Bubble1, DateRow2, Bubble2]`.
  - Message.System(Joined) с Persona.gender=Female → text "Мария Климова присоединилась к группе".
  - Message.System(Joined) с Persona.gender=Male → text "Алексей Гурин присоединился к группе".
  - Сообщение от unknown senderId → fallback "Кто-то".
  - `dateForRow` корректно мапит каждый row на его дату.

- **`ComputeGroupPositionsTest`:**
  - Два same-sender bubble в одной группе (<5мин, same senderId/isMine) → FIRST + LAST.
  - Те же bubble с SystemRow между ними → SINGLE + SINGLE.
  - Три same-sender + один разрыв SystemRow посередине → FIRST + LAST, SINGLE.

- **`ComputeTopPaddingTest`:**
  - Проверить все 6 cases из таблицы.

UI-тесты (если есть Compose UI testing setup) — оставляем на impl plan.

## Связь с существующими фичами

- **Reply/Edit на System.** Reply/Edit-меню на SystemRow не открывается (system row не имеет `onBubbleTap`). SwipeToReplyItem применяется только к Bubble (см. items()-branch). Никаких правок в `ChatDetailViewModel`.
- **Reactions.** SystemRow не имеет reactions (поле в `Message.System` есть, но игнорируется). UI рендера реакций — только в Bubble-ветке.
- **Авто-синтез preview-bubble** в `MockRepositoryImpl.appendIncomingPreviewIfNeeded` — без изменений (синтезирует Message.Text, попадает в messages-list до flatten).
- **`isP2P` параметр MessageList** — без изменений (применяется к Bubble-ветке, не к SystemRow).

## Известные ограничения

- Sticky overlay — overlay поверх контента, не reserved-padding. Кратковременный overlap верхнего баббла во время скролла осознанно принят (Telegram-стиль).
- Цвета SystemMessageRow (`Color.Black.copy(alpha=0.30f)`, `Color.White`) — синтезированы, не из DSColors. Причина — отсутствие подходящего бренд-токена для over-pattern overlay и несовместимость DSColors.basicNN с app-toggle темой (см. memory feedback-app-isdark-ds-colors). Будет заменено канонической палитрой DS-компонента, когда он появится.
- Pure-Compose pill стиль — TEMP. Когда придёт компонент из android-components — заменим SystemMessageRow на него, но всю остальную инфраструктуру (RowItem, flatten, grouping, sticky overlay) оставляем как есть.

## Implementation plan

См. `docs/superpowers/plans/2026-05-21-system-messages-implementation.md`.
