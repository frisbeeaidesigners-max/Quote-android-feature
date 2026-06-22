# System Messages + Sticky Date Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ввести `Message.System(kind=Joined)` в data-модель, unified RowItem-абстракцию в `MessageList`, общее правило группировки 4dp/8dp для system rows, и sticky date overlay поверх LazyColumn с fade через 2с idle. Заменяет existing TEMP `DateSeparator` на TEMP `SystemMessageRow` (тот же стиль, но универсальный для всех system-row).

**Architecture:** Pure data — `Message.System` через тот же `classDiscriminator='type'` sealed-механизм. Pure-Kotlin logic (`flattenToRows`, `computeGroupPositions`, `computeTopPadding`, `renderSystemText`) выносится в отдельный файл `core/ui/.../hosts/MessageRows.kt` с `internal` visibility и unit-тестами в `core/ui/src/test/`. `MessageList.kt` рефакторится: items по `List<RowItem>`, branching на SystemRow vs Bubble. Sticky — кастомный overlay в `Box`, читает `LazyListState.layoutInfo.visibleItemsInfo.last()`, `LaunchedEffect(isScrollInProgress)` с `delay(2000)`.

**Tech Stack:** Kotlin 1.9.22, AGP 8.2.2, Compose BOM 2024.02.00, kotlinx-serialization, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-05-21-system-messages-design.md`

---

## File Structure

**Создаются:**
- `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageRows.kt` — RowItem sealed, FlattenedRows, flattenToRows, renderSystemText, computeGroupPositions, computeTopPadding (всё `internal`).
- `core/ui/src/test/java/com/example/template/core/ui/hosts/MessageRowsTest.kt` — unit-тесты для функций выше.
- `app/src/main/assets/mock/messages/grp-01.json`, `grp-06.json`, `grp-12.json` — мок-сообщения с Joined-примерами.

**Модифицируются:**
- `core/model/src/main/java/com/example/template/core/model/Persona.kt` — добавить `Gender` enum + `gender` поле в Persona.
- `core/model/src/main/java/com/example/template/core/model/Message.kt` — добавить `SystemKind` enum + `Message.System` sealed-вариант.
- `app/src/main/assets/mock/personas.json` — проставить `gender` каждой персоне.
- `core/ui/build.gradle.kts` — добавить `testImplementation(libs.junit)`.
- `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt` — рефактор items()-блока на RowItem, замена `DateSeparator` на `SystemMessageRow`, добавление `StickyDateOverlay`, удаление locally-computed `daySeparators` и `topPadding`-логики.
- `CLAUDE.md` — обновить раздел Bubbles упоминание про system messages + sticky.

**Memory (local, не commit'ится):**
- `~/.claude/.../memory/project_date_separator_temp.md` → переименовать в `project_system_message_row_temp.md`, обновить содержимое.
- `~/.claude/.../memory/MEMORY.md` — обновить ссылку.

---

## Task 1: Gender в Persona + JSON

**Files:**
- Modify: `core/model/src/main/java/com/example/template/core/model/Persona.kt`
- Modify: `app/src/main/assets/mock/personas.json`

- [ ] **Step 1.1: Добавить Gender enum + gender поле в Persona**

Открыть `core/model/src/main/java/com/example/template/core/model/Persona.kt` и заменить содержимое на (сохраняя существующие computed-properties `fullName` и `initials`):

```kotlin
package com.example.template.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Gender { Male, Female }

@Serializable
data class Persona(
    val id: String,
    val firstName: String,
    val lastName: String,
    val avatarAsset: String? = null,
    val gradientIndex: Int = 0,
    val gender: Gender,
) {
    val fullName: String get() = "$firstName $lastName".trim()
    val initials: String get() =
        (firstName.firstOrNull()?.toString().orEmpty() + lastName.firstOrNull()?.toString().orEmpty()).uppercase()
}
```

(Изменение: добавлены `Gender` enum и `gender: Gender` поле. Computed properties не трогаем.)

- [ ] **Step 1.2: Обновить personas.json — gender для всех 14 записей**

`app/src/main/assets/mock/personas.json`:

```json
[
  {"id": "pe-self", "firstName": "Мы",      "lastName": "",          "avatarAsset": "images/avatar_00.png", "gradientIndex": 0,  "gender": "Male"},
  {"id": "pe-01",   "firstName": "Мария",   "lastName": "Климова",   "avatarAsset": "images/avatar_01.png", "gradientIndex": 1,  "gender": "Female"},
  {"id": "pe-02",   "firstName": "Виктория","lastName": "Нечаева",   "avatarAsset": "images/avatar_02.png", "gradientIndex": 2,  "gender": "Female"},
  {"id": "pe-03",   "firstName": "Екатерина","lastName": "Орлова",   "avatarAsset": "images/avatar_03.png", "gradientIndex": 3,  "gender": "Female"},
  {"id": "pe-04",   "firstName": "Софья",   "lastName": "Баженова",  "avatarAsset": "images/avatar_04.png", "gradientIndex": 4,  "gender": "Female"},
  {"id": "pe-05",   "firstName": "Анна",    "lastName": "Ковалёва",  "avatarAsset": "images/avatar_05.png", "gradientIndex": 5,  "gender": "Female"},
  {"id": "pe-06",   "firstName": "Дарья",   "lastName": "Лапшина",   "avatarAsset": "images/avatar_06.png", "gradientIndex": 6,  "gender": "Female"},
  {"id": "pe-07",   "firstName": "Ирина",   "lastName": "Соловьёва", "avatarAsset": "images/avatar_07.png", "gradientIndex": 7,  "gender": "Female"},
  {"id": "pe-08",   "firstName": "Алексей", "lastName": "Гурин",     "avatarAsset": "images/avatar_08.png", "gradientIndex": 8,  "gender": "Male"},
  {"id": "pe-09",   "firstName": "Павел",   "lastName": "Сердюков",  "avatarAsset": "images/avatar_09.png", "gradientIndex": 9,  "gender": "Male"},
  {"id": "pe-10",   "firstName": "Дмитрий", "lastName": "Федоров",   "avatarAsset": null,                   "gradientIndex": 0,  "gender": "Male"},
  {"id": "pe-11",   "firstName": "Николай", "lastName": "Павлов",    "avatarAsset": null,                   "gradientIndex": 4,  "gender": "Male"},
  {"id": "pe-12",   "firstName": "Александр","lastName": "Иванов",   "avatarAsset": null,                   "gradientIndex": 6,  "gender": "Male"},
  {"id": "pe-13",   "firstName": "Ольга",   "lastName": "Орехова",   "avatarAsset": null,                   "gradientIndex": 10, "gender": "Female"}
]
```

- [ ] **Step 1.3: Прогнать существующие тесты, чтобы убедиться что parsing не сломался**

PowerShell:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; .\gradlew.bat :core:data:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`. Все existing-тесты (JsonParsingTest и т.д.) проходят.

- [ ] **Step 1.4: Commit**

```bash
git add core/model/src/main/java/com/example/template/core/model/Persona.kt app/src/main/assets/mock/personas.json
git commit -m "feat(model): add Gender field to Persona

Будет использовано в renderSystemText для выбора суффикса глагола
в Joined-системном сообщении ('присоединилась' vs 'присоединился')."
```

---

## Task 2: Message.System в core/model

**Files:**
- Modify: `core/model/src/main/java/com/example/template/core/model/Message.kt`

- [ ] **Step 2.1: Добавить SystemKind enum + Message.System sealed-вариант**

В `core/model/src/main/java/com/example/template/core/model/Message.kt` после существующих enum'ов (рядом с `CallStatus`, `SpanStyle`) добавить:

```kotlin
@Serializable
enum class SystemKind { Joined }
```

И внутри `sealed class Message` (после `data class CallMeet`) добавить:

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
```

- [ ] **Step 2.2: Прогнать тесты на парсинг**

Run:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; .\gradlew.bat :core:data:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`. Existing-тесты проходят (новый Message.System не используется в существующих фикстурах, sealed-добавление не ломает совместимость).

- [ ] **Step 2.3: Commit**

```bash
git add core/model/src/main/java/com/example/template/core/model/Message.kt
git commit -m "feat(model): add Message.System(kind=Joined) sealed variant

Подключается через тот же classDiscriminator='type' что и остальные
Message-вариации (text/media/voice/link/callmeet); JSON-тэг 'system'."
```

---

## Task 3: Unit-test infra в core/ui

**Files:**
- Modify: `core/ui/build.gradle.kts`
- Create: `core/ui/src/test/java/com/example/template/core/ui/hosts/MessageRowsTest.kt` (пустой smoke)

- [ ] **Step 3.1: Добавить testImplementation в core/ui/build.gradle.kts**

Открыть `core/ui/build.gradle.kts`, в блоке `dependencies { ... }` добавить:

```kotlin
testImplementation(libs.junit)
testImplementation(project(":core:model"))
```

(Второй — потому что unit-тесты буду оперировать `Message`, `Persona`, `Gender` напрямую.)

- [ ] **Step 3.2: Создать пустой smoke-тест**

Create `core/ui/src/test/java/com/example/template/core/ui/hosts/MessageRowsTest.kt`:

```kotlin
package com.example.template.core.ui.hosts

import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRowsTest {
    @Test
    fun smoke() {
        assertTrue(true)
    }
}
```

- [ ] **Step 3.3: Прогнать тесты — убедиться что infra работает**

Run:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; .\gradlew.bat :core:ui:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, smoke-тест PASS.

- [ ] **Step 3.4: Commit**

```bash
git add core/ui/build.gradle.kts core/ui/src/test/java/com/example/template/core/ui/hosts/MessageRowsTest.kt
git commit -m "chore(core-ui): add unit test infra with junit"
```

---

## Task 4: RowItem sealed + flattenToRows (TDD)

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageRows.kt`
- Modify: `core/ui/src/test/java/com/example/template/core/ui/hosts/MessageRowsTest.kt`

- [ ] **Step 4.1: Создать MessageRows.kt с RowItem sealed**

Create `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageRows.kt`:

```kotlin
package com.example.template.core.ui.hosts

import com.example.template.core.model.Message
import com.example.template.core.model.Persona

internal sealed class RowItem {
    abstract val key: String
    abstract val timestamp: Long

    data class Bubble(val message: Message) : RowItem() {
        override val key: String get() = message.id
        override val timestamp: Long get() = message.timestamp
    }

    data class SystemRow(
        val text: String,
        override val timestamp: Long,
        override val key: String,
        val isDate: Boolean,
    ) : RowItem()
}

internal data class FlattenedRows(
    val rows: List<RowItem>,
    val dateForRow: Map<String, String>,
)
```

- [ ] **Step 4.2: Написать failing-тест: empty messages**

Открыть `MessageRowsTest.kt`, заменить smoke-тест:

```kotlin
package com.example.template.core.ui.hosts

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageRowsTest {
    @Test
    fun `flattenToRows returns empty for empty messages`() {
        val result = flattenToRows(
            messages = emptyList(),
            now = 0L,
            personaByUserId = { null },
            formatDate = { "" },
        )
        assertEquals(emptyList<RowItem>(), result.rows)
        assertEquals(emptyMap<String, String>(), result.dateForRow)
    }
}
```

- [ ] **Step 4.3: Прогнать — должен фейлиться "function not defined"**

Run:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; .\gradlew.bat :core:ui:testDebugUnitTest
```

Expected: компиляция падает с "Unresolved reference: flattenToRows".

- [ ] **Step 4.4: Минимальная реализация (только empty)**

В `MessageRows.kt` добавить:

```kotlin
internal fun flattenToRows(
    messages: List<Message>,
    now: Long,
    personaByUserId: (String) -> Persona?,
    formatDate: (Long) -> String,
): FlattenedRows {
    return FlattenedRows(emptyList(), emptyMap())
}
```

- [ ] **Step 4.5: Прогнать тест — должен PASS**

Run то же. Expected: 1 test PASS.

- [ ] **Step 4.6: Добавить failing-тест: single Text message → [DateRow, Bubble]**

```kotlin
@Test
fun `flattenToRows emits DateRow then Bubble for single text message`() {
    val msg = Message.Text(
        id = "m-1", chatId = "c-1", senderId = "u-1",
        timestamp = 1715000000_000L, isMine = false, body = "hi",
    )
    val result = flattenToRows(
        messages = listOf(msg),
        now = 1715000000_000L,
        personaByUserId = { null },
        formatDate = { "Сегодня" },
    )
    assertEquals(2, result.rows.size)
    val dateRow = result.rows[0] as RowItem.SystemRow
    assertEquals("Сегодня", dateRow.text)
    assertTrue(dateRow.isDate)
    val bubble = result.rows[1] as RowItem.Bubble
    assertEquals("m-1", bubble.key)
}
```

Импорты добавить: `import com.example.template.core.model.Message`, `import org.junit.Assert.assertTrue`.

- [ ] **Step 4.7: Прогнать тест — должен FAIL (rows.size = 0)**

- [ ] **Step 4.8: Расширить реализацию для одного дня**

```kotlin
internal fun flattenToRows(
    messages: List<Message>,
    now: Long,
    personaByUserId: (String) -> Persona?,
    formatDate: (Long) -> String,
): FlattenedRows {
    if (messages.isEmpty()) return FlattenedRows(emptyList(), emptyMap())
    val rows = mutableListOf<RowItem>()
    val dateForRow = HashMap<String, String>()
    var prevDayKey: Int? = null
    var currentDateLabel: String? = null
    val cal = java.util.Calendar.getInstance()
    for (msg in messages) {
        cal.timeInMillis = msg.timestamp
        val dayKey = cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
        if (dayKey != prevDayKey) {
            currentDateLabel = formatDate(msg.timestamp)
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
        val row: RowItem = when (msg) {
            is Message.System -> error("System messages handled in next task")
            else -> RowItem.Bubble(msg)
        }
        rows.add(row)
        dateForRow[row.key] = currentDateLabel ?: ""
    }
    return FlattenedRows(rows, dateForRow)
}
```

- [ ] **Step 4.9: Прогнать тесты — оба PASS**

- [ ] **Step 4.10: Failing test: two messages same day → [DateRow, Bubble, Bubble]**

```kotlin
@Test
fun `flattenToRows groups two messages of same day under one DateRow`() {
    val day = 1715000000_000L
    val m1 = Message.Text(id="m-1", chatId="c-1", senderId="u-1", timestamp=day, isMine=false, body="a")
    val m2 = Message.Text(id="m-2", chatId="c-1", senderId="u-1", timestamp=day + 60_000L, isMine=false, body="b")
    val r = flattenToRows(listOf(m1, m2), day, { null }, { "Сегодня" })
    assertEquals(3, r.rows.size)
    assertTrue(r.rows[0] is RowItem.SystemRow)
    assertEquals("m-1", (r.rows[1] as RowItem.Bubble).key)
    assertEquals("m-2", (r.rows[2] as RowItem.Bubble).key)
}
```

Прогнать — должен PASS сразу (логика уже корректна для same-day).

- [ ] **Step 4.11: Failing test: two messages DIFFERENT days → [DateRow1, Bubble1, DateRow2, Bubble2]**

```kotlin
@Test
fun `flattenToRows emits new DateRow on day boundary`() {
    val day1 = 1715000000_000L           // some day
    val day2 = day1 + 24 * 3600 * 1000L  // next day
    val m1 = Message.Text(id="m-1", chatId="c-1", senderId="u-1", timestamp=day1, isMine=false, body="a")
    val m2 = Message.Text(id="m-2", chatId="c-1", senderId="u-1", timestamp=day2, isMine=false, body="b")
    var calls = 0
    val r = flattenToRows(listOf(m1, m2), day2, { null }, { ts -> "Date-${calls++}" })
    assertEquals(4, r.rows.size)
    assertEquals("Date-0", (r.rows[0] as RowItem.SystemRow).text)
    assertEquals("m-1", (r.rows[1] as RowItem.Bubble).key)
    assertEquals("Date-1", (r.rows[2] as RowItem.SystemRow).text)
    assertEquals("m-2", (r.rows[3] as RowItem.Bubble).key)
}
```

Прогнать — должен PASS сразу.

- [ ] **Step 4.12: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessageRows.kt core/ui/src/test/java/com/example/template/core/ui/hosts/MessageRowsTest.kt
git commit -m "feat(core-ui): flattenToRows with day-boundary DateRow injection"
```

---

## Task 5: renderSystemText + flattenToRows для Message.System (TDD)

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageRows.kt`
- Modify: `core/ui/src/test/java/com/example/template/core/ui/hosts/MessageRowsTest.kt`

- [ ] **Step 5.1: Failing-тест: Female Joined**

В MessageRowsTest.kt:

```kotlin
@Test
fun `renderSystemText returns female form for female persona`() {
    val persona = Persona(
        id = "pe-1", firstName = "Мария", lastName = "Климова",
        avatarAsset = null, gradientIndex = 1, gender = Gender.Female,
    )
    val msg = Message.System(
        id = "s-1", chatId = "c-1", senderId = "u-1",
        timestamp = 0L, kind = SystemKind.Joined,
    )
    val text = renderSystemText(msg) { if (it == "u-1") persona else null }
    assertEquals("Мария Климова присоединилась к группе", text)
}
```

Импорты: `import com.example.template.core.model.*`.

- [ ] **Step 5.2: Прогнать — должен FAIL "Unresolved renderSystemText"**

- [ ] **Step 5.3: Минимальная реализация renderSystemText**

В MessageRows.kt:

```kotlin
internal fun renderSystemText(
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

Import `com.example.template.core.model.Gender`, `SystemKind`.

- [ ] **Step 5.4: Прогнать — PASS**

- [ ] **Step 5.5: Failing-тест: Male Joined**

```kotlin
@Test
fun `renderSystemText returns male form for male persona`() {
    val persona = Persona(
        id = "pe-1", firstName = "Алексей", lastName = "Гурин",
        avatarAsset = null, gradientIndex = 8, gender = Gender.Male,
    )
    val msg = Message.System(
        id = "s-1", chatId = "c-1", senderId = "u-1",
        timestamp = 0L, kind = SystemKind.Joined,
    )
    val text = renderSystemText(msg) { persona }
    assertEquals("Алексей Гурин присоединился к группе", text)
}
```

Прогнать — должен PASS сразу.

- [ ] **Step 5.6: Failing-тест: unknown sender → "Кто-то"**

```kotlin
@Test
fun `renderSystemText falls back to neutral when persona unknown`() {
    val msg = Message.System(
        id = "s-1", chatId = "c-1", senderId = "u-unknown",
        timestamp = 0L, kind = SystemKind.Joined,
    )
    val text = renderSystemText(msg) { null }
    assertEquals("Кто-то присоединился к группе", text)
}
```

Прогнать — должен PASS сразу.

- [ ] **Step 5.7: Failing-тест: flattenToRows c Message.System → SystemRow с правильным текстом**

```kotlin
@Test
fun `flattenToRows emits SystemRow for Message_System`() {
    val persona = Persona(
        id = "pe-1", firstName = "Мария", lastName = "Климова",
        avatarAsset = null, gradientIndex = 1, gender = Gender.Female,
    )
    val sysMsg = Message.System(
        id = "s-1", chatId = "c-1", senderId = "u-1",
        timestamp = 1715000000_000L, kind = SystemKind.Joined,
    )
    val r = flattenToRows(listOf(sysMsg), 1715000000_000L, { persona }, { "Сегодня" })
    assertEquals(2, r.rows.size)
    val sysRow = r.rows[1] as RowItem.SystemRow
    assertEquals("sys-s-1", sysRow.key)
    assertEquals("Мария Климова присоединилась к группе", sysRow.text)
    assertFalse(sysRow.isDate)
}
```

Import: `import org.junit.Assert.assertFalse`.

- [ ] **Step 5.8: Прогнать — должен FAIL с `IllegalStateException("System messages handled in next task")`**

- [ ] **Step 5.9: Расширить flattenToRows: branch на Message.System**

В flattenToRows заменить:

```kotlin
val row: RowItem = when (msg) {
    is Message.System -> error("System messages handled in next task")
    else -> RowItem.Bubble(msg)
}
```

на:

```kotlin
val row: RowItem = when (msg) {
    is Message.System -> RowItem.SystemRow(
        text = renderSystemText(msg, personaByUserId),
        timestamp = msg.timestamp,
        key = "sys-${msg.id}",
        isDate = false,
    )
    else -> RowItem.Bubble(msg)
}
```

- [ ] **Step 5.10: Прогнать — все 7 тестов PASS**

- [ ] **Step 5.11: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessageRows.kt core/ui/src/test/java/com/example/template/core/ui/hosts/MessageRowsTest.kt
git commit -m "feat(core-ui): renderSystemText + Joined SystemRow in flattenToRows"
```

---

## Task 6: computeGroupPositions(rows) с system-row breaker (TDD)

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageRows.kt`
- Modify: `core/ui/src/test/java/com/example/template/core/ui/hosts/MessageRowsTest.kt`

- [ ] **Step 6.1: Failing-тест: two same-sender bubbles → FIRST + LAST**

```kotlin
@Test
fun `computeGroupPositions groups two close same-sender bubbles`() {
    val t = 1715000000_000L
    val m1 = Message.Text(id="m-1", chatId="c-1", senderId="u-1", timestamp=t, isMine=false, body="a")
    val m2 = Message.Text(id="m-2", chatId="c-1", senderId="u-1", timestamp=t + 60_000L, isMine=false, body="b")
    val rows = listOf<RowItem>(RowItem.Bubble(m1), RowItem.Bubble(m2))
    val pos = computeGroupPositions(rows)
    assertEquals(GroupPosition.FIRST, pos["m-1"])
    assertEquals(GroupPosition.LAST, pos["m-2"])
}
```

Tест ссылается на `GroupPosition` и `computeGroupPositions(List<RowItem>)` — пока этого в MessageRows.kt нет (есть в MessageList.kt private).

- [ ] **Step 6.2: Прогнать — FAIL "Unresolved GroupPosition / computeGroupPositions"**

- [ ] **Step 6.3: Перенести GroupPosition + sameGroup + computeGroupPositions в MessageRows.kt**

В MessageRows.kt добавить:

```kotlin
internal enum class GroupPosition { SINGLE, FIRST, MIDDLE, LAST }

internal const val GROUP_TIME_THRESHOLD_MS = 5 * 60 * 1000L

internal fun sameGroup(a: Message, b: Message): Boolean =
    a.senderId == b.senderId &&
        a.isMine == b.isMine &&
        kotlin.math.abs(b.timestamp - a.timestamp) <= GROUP_TIME_THRESHOLD_MS

internal fun computeGroupPositions(rows: List<RowItem>): Map<String, GroupPosition> {
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

- [ ] **Step 6.4: Прогнать — тест PASS**

- [ ] **Step 6.5: Failing-тест: SystemRow между bubble'ами — breaker**

```kotlin
@Test
fun `computeGroupPositions treats SystemRow as breaker between same-sender bubbles`() {
    val t = 1715000000_000L
    val m1 = Message.Text(id="m-1", chatId="c-1", senderId="u-1", timestamp=t, isMine=false, body="a")
    val sys = RowItem.SystemRow(text="X joined", timestamp=t + 30_000L, key="sys-1", isDate=false)
    val m2 = Message.Text(id="m-2", chatId="c-1", senderId="u-1", timestamp=t + 60_000L, isMine=false, body="b")
    val rows = listOf<RowItem>(RowItem.Bubble(m1), sys, RowItem.Bubble(m2))
    val pos = computeGroupPositions(rows)
    assertEquals(GroupPosition.SINGLE, pos["m-1"])
    assertEquals(GroupPosition.SINGLE, pos["m-2"])
}
```

- [ ] **Step 6.6: Прогнать — PASS (логика уже корректна)**

- [ ] **Step 6.7: Failing-тест: different senders → SINGLE + SINGLE**

```kotlin
@Test
fun `computeGroupPositions does not group different senders`() {
    val t = 1715000000_000L
    val m1 = Message.Text(id="m-1", chatId="c-1", senderId="u-1", timestamp=t, isMine=false, body="a")
    val m2 = Message.Text(id="m-2", chatId="c-1", senderId="u-2", timestamp=t + 60_000L, isMine=false, body="b")
    val rows = listOf<RowItem>(RowItem.Bubble(m1), RowItem.Bubble(m2))
    val pos = computeGroupPositions(rows)
    assertEquals(GroupPosition.SINGLE, pos["m-1"])
    assertEquals(GroupPosition.SINGLE, pos["m-2"])
}
```

- [ ] **Step 6.8: Прогнать — PASS**

- [ ] **Step 6.9: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessageRows.kt core/ui/src/test/java/com/example/template/core/ui/hosts/MessageRowsTest.kt
git commit -m "feat(core-ui): computeGroupPositions on RowItem with SystemRow as breaker"
```

---

## Task 7: computeTopPadding (TDD)

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageRows.kt`
- Modify: `core/ui/src/test/java/com/example/template/core/ui/hosts/MessageRowsTest.kt`

- [ ] **Step 7.1: Failing-тест: первый row → 0dp**

```kotlin
@Test
fun `computeTopPadding returns zero for first row`() {
    val t = 1715000000_000L
    val sys = RowItem.SystemRow(text="d", timestamp=t, key="date-1", isDate=true)
    val pad = computeTopPadding(listOf(sys), emptyMap())
    assertEquals(0, pad["date-1"]?.value?.toInt())
}
```

Будем хранить топ-паддинги в `Map<String, androidx.compose.ui.unit.Dp>`. Compose-импорт нужен в pure-логике — это OK, у Compose Dp нет android-зависимостей в runtime.

Импорты в test: `import androidx.compose.ui.unit.Dp`.

- [ ] **Step 7.2: Прогнать — FAIL "Unresolved computeTopPadding"**

- [ ] **Step 7.3: Минимальная реализация (только первый row=0dp)**

В MessageRows.kt добавить (с импортами `androidx.compose.ui.unit.Dp`, `androidx.compose.ui.unit.dp`):

```kotlin
internal fun computeTopPadding(
    rows: List<RowItem>,
    groupPositions: Map<String, GroupPosition>,
): Map<String, androidx.compose.ui.unit.Dp> {
    val map = HashMap<String, androidx.compose.ui.unit.Dp>(rows.size)
    for (i in rows.indices) {
        val cur = rows[i]
        val prev = rows.getOrNull(i - 1)
        val pad: androidx.compose.ui.unit.Dp = if (prev == null) 0.dp else 0.dp
        map[cur.key] = pad
    }
    return map
}
```

- [ ] **Step 7.4: Прогнать — PASS**

- [ ] **Step 7.5: Добавить failing-тесты для всех 5 оставшихся cases из таблицы**

```kotlin
private fun bubble(id: String, sender: String, ts: Long): RowItem.Bubble =
    RowItem.Bubble(Message.Text(id=id, chatId="c", senderId=sender, timestamp=ts, isMine=false, body=""))
private fun sysDate(key: String, ts: Long): RowItem.SystemRow =
    RowItem.SystemRow(text="", timestamp=ts, key=key, isDate=true)
private fun sysJoin(key: String, ts: Long): RowItem.SystemRow =
    RowItem.SystemRow(text="", timestamp=ts, key=key, isDate=false)

@Test fun `topPadding SystemRow after SystemRow is 4dp`() {
    val rows = listOf(sysDate("a", 0L), sysJoin("b", 1L))
    val pad = computeTopPadding(rows, emptyMap())
    assertEquals(4, pad["b"]?.value?.toInt())
}

@Test fun `topPadding SystemRow after Bubble is 8dp`() {
    val rows = listOf(bubble("m1", "u1", 0L), sysJoin("s", 1L))
    val groupPos = computeGroupPositions(rows)
    val pad = computeTopPadding(rows, groupPos)
    assertEquals(8, pad["s"]?.value?.toInt())
}

@Test fun `topPadding Bubble after SystemRow is 8dp`() {
    val rows = listOf(sysDate("d", 0L), bubble("m1", "u1", 1L))
    val groupPos = computeGroupPositions(rows)
    val pad = computeTopPadding(rows, groupPos)
    assertEquals(8, pad["m1"]?.value?.toInt())
}

@Test fun `topPadding Bubble joining prev group is 4dp`() {
    val t = 1715000000_000L
    val rows = listOf(bubble("m1", "u1", t), bubble("m2", "u1", t + 60_000L))
    val groupPos = computeGroupPositions(rows)
    val pad = computeTopPadding(rows, groupPos)
    assertEquals(4, pad["m2"]?.value?.toInt())
}

@Test fun `topPadding Bubble starting new group is 8dp`() {
    val rows = listOf(bubble("m1", "u1", 0L), bubble("m2", "u2", 1L))
    val groupPos = computeGroupPositions(rows)
    val pad = computeTopPadding(rows, groupPos)
    assertEquals(8, pad["m2"]?.value?.toInt())
}
```

- [ ] **Step 7.6: Прогнать — должно быть 4 FAIL (Bubble after Bubble joining работает случайно — проверить)**

- [ ] **Step 7.7: Полная реализация computeTopPadding**

Заменить тело функции:

```kotlin
internal fun computeTopPadding(
    rows: List<RowItem>,
    groupPositions: Map<String, GroupPosition>,
): Map<String, androidx.compose.ui.unit.Dp> {
    val map = HashMap<String, androidx.compose.ui.unit.Dp>(rows.size)
    for (i in rows.indices) {
        val cur = rows[i]
        val prev = rows.getOrNull(i - 1)
        val pad: androidx.compose.ui.unit.Dp = when {
            prev == null -> 0.dp
            cur is RowItem.SystemRow && prev is RowItem.SystemRow -> 4.dp
            cur is RowItem.SystemRow && prev is RowItem.Bubble -> 8.dp
            cur is RowItem.Bubble && prev is RowItem.SystemRow -> 8.dp
            cur is RowItem.Bubble && prev is RowItem.Bubble -> {
                val pos = groupPositions[cur.message.id]
                if (pos == GroupPosition.FIRST || pos == GroupPosition.SINGLE) 8.dp else 4.dp
            }
            else -> 0.dp
        }
        map[cur.key] = pad
    }
    return map
}
```

- [ ] **Step 7.8: Прогнать — все 6 тестов PASS**

- [ ] **Step 7.9: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessageRows.kt core/ui/src/test/java/com/example/template/core/ui/hosts/MessageRowsTest.kt
git commit -m "feat(core-ui): computeTopPadding by row-pair pattern (4dp/8dp)"
```

---

## Task 8: Замена DateSeparator на SystemMessageRow в MessageList

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt`

- [ ] **Step 8.1: Удалить private fun DateSeparator + блок вычисления daySeparators**

В MessageList.kt:
- удалить `private fun DateSeparator(...)` (комментарий ВРЕМЕННОЕ РЕШЕНИЕ + сам fun)
- удалить блок `val daySeparators: Map<String, String> = remember(messages) { ... }`
- удалить `import java.util.Calendar` (станет не нужен после удаления daySeparators)
- удалить `import com.example.template.core.ui.format.TimeFormatter` если он стал не нужен (TimeFormatter всё ещё используется для формата времени bubble, проверить — `TimeFormatter.formatBubbleTime` остаётся)

- [ ] **Step 8.2: Добавить private fun SystemMessageRow**

В то же место, где был DateSeparator:

```kotlin
// ВРЕМЕННОЕ РЕШЕНИЕ. Плейсхолдер для всех system-row'ов (даты и Joined-системные).
// Когда в android-components появится канонический DS-компонент — заменить вызов
// на него и удалить этот fun. Цвета синтезируем через Color.Black/.copy(alpha=...) +
// Color.White — плашка лежит поверх brand-pattern'а и должна одинаково читаться
// в светлой и тёмной теме приложения (см. memory: feedback-app-isdark-ds-colors).
// Никаких внутренних vertical padding — top-зазор контролируется caller'ом.
@Composable
private fun SystemMessageRow(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.30f))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Text(
                text = text,
                color = Color.White,
                style = DSTypography.subhead4M.toComposeTextStyle(),
            )
        }
    }
}
```

(Импорты уже есть после Task'а с DateSeparator.)

- [ ] **Step 8.3: Build — убедиться что код компилируется (даже если daySeparators ещё используется в items() — fix дальше)**

Если ссылки на `DateSeparator` или `daySeparators` остались в items() — это ожидаемо. Перейдём к Task 9 — там полный рефактор items().

Run:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; .\gradlew.bat :core:ui:compileDebugKotlin
```

Expected: FAIL — `DateSeparator` not found в items(). Это нормально, исправим в Task 9.

- [ ] **Step 8.4: Закоммитим частичный прогресс**

(Опционально — можно объединить с Task 9 в один коммит. Для атомарности commit'ов лучше сделать промежуточный.)

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt
git commit -m "refactor(core-ui): replace DateSeparator placeholder with SystemMessageRow

WIP — items() block ещё ссылается на удалённые daySeparators/DateSeparator,
fix в следующем коммите (полный рефактор на RowItem)."
```

---

## Task 9: Рефактор items() block — RowItem branching

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt`

- [ ] **Step 9.1: Заменить вычисления groupPositions / reversed / messagesById на RowItem-based**

Текущий блок:
```kotlin
val groupPositions: Map<String, GroupPosition> = remember(messages) { computeGroupPositions(messages) }
val daySeparators: Map<String, String> = remember(messages) { ... }
val reversed = remember(messages) { messages.asReversed() }
val messagesById = remember(messages) { messages.associateBy { it.id } }
```

Заменить на:

```kotlin
val now = remember(messages) { System.currentTimeMillis() }
val flattened = remember(messages, now, personaByUserId) {
    flattenToRows(messages, now, personaByUserId) { ts ->
        TimeFormatter.formatDateSeparator(ts, now)
    }
}
val rows = flattened.rows
val dateForRow = flattened.dateForRow
val reversed = remember(rows) { rows.asReversed() }
val groupPositions = remember(rows) { computeGroupPositions(rows) }
val topPaddingPerRow = remember(rows, groupPositions) { computeTopPadding(rows, groupPositions) }
val messagesById = remember(messages) { messages.associateBy { it.id } }
```

(Заметка: импорт `TimeFormatter` остаётся обязательно.)

Также удалить `private enum class GroupPosition`, `private const val GROUP_TIME_THRESHOLD_MS`, `private fun sameGroup`, `private fun computeGroupPositions(messages: List<Message>)` — они переехали в MessageRows.kt.

- [ ] **Step 9.2: items() block — branch на RowItem**

Заменить весь блок:
```kotlin
items(reversed, key = { it.id }) { msg ->
    ... ВСЯ существующая логика баббла ...
}
```

на:

```kotlin
items(reversed, key = { it.key }) { row ->
    val topPad = topPaddingPerRow[row.key] ?: 0.dp
    when (row) {
        is RowItem.SystemRow -> SystemMessageRow(
            text = row.text,
            modifier = Modifier.padding(top = topPad),
        )
        is RowItem.Bubble -> {
            val msg = row.message
            val position = groupPositions[msg.id] ?: GroupPosition.SINGLE
            val isFirstInGroup = position == GroupPosition.FIRST || position == GroupPosition.SINGLE
            val isLastInGroup = position == GroupPosition.LAST || position == GroupPosition.SINGLE
            @Suppress("UNUSED_VARIABLE") val cacheVersionRead = cacheVersion
            val persona: Persona? = if (!msg.isMine) personaByUserId(msg.senderId) else null
            val senderName = if (isP2P) "" else persona?.let { "${it.firstName} ${it.lastName}" }.orEmpty()
            val senderInitials = persona?.initials
            val senderAvatar: Bitmap? = bitmapCache.get(persona?.avatarAsset)
            val avatarSchemeIdx = (persona?.gradientIndex ?: 0)
                .coerceAtLeast(0)
                .let { it % avatarSchemesByIndex.size.coerceAtLeast(1) }
            val avatarScheme = avatarSchemesByIndex[avatarSchemeIdx]
                ?: avatarSchemesByIndex.values.firstOrNull()
                ?: AvatarColorScheme.DEFAULT
            val replySender = msg.replyTo?.authorName?.replace(' ', ' ')
            val replyText = resolveReplyText(msg, messagesById)
            val bubbleRightPxState = remember(msg.id) { mutableStateOf(0) }
            val rowWidthPxState = remember(msg.id) { mutableStateOf(0) }
            val rowYAdjustmentState = remember(msg.id) { mutableStateOf(0f) }
            SwipeToReplyItem(
                messageId = msg.id,
                enabled = msg is Message.Text || msg is Message.Media,
                onTriggerReply = onSwipeReply,
                modifier = Modifier.fillMaxWidth(),
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPad)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onBubbleTap(msg.id) },
            ) {
                // ... ВСЁ существующее содержимое Column'а — bubble row Box, реакции, и т.д. ...
                // ВАЖНО: внешний Column (с DateSeparator) который добавлялся в прошлой
                // итерации — УДАЛИТЬ. SystemMessageRow теперь отдельным items()-ветка.
            }
            } // SwipeToReplyItem
        }
    }
}
```

(Содержимое внутреннего `Column { ... }` в Bubble-ветке остаётся БЕЗ изменений — те же AndroidView для bubble, реакций и т.д. Меняется только: убран outer Column с DateSeparator из прошлой итерации; `topPadding` → `topPad`.)

- [ ] **Step 9.3: Обновить ключ latestId-эффекта авто-скролла**

```kotlin
// Оставить как есть: messages.lastOrNull()?.id — НЕ row.key, чтобы добавление
// синтетических DateRow'ов не дёргало animateScrollToItem.
val latestId = messages.lastOrNull()?.id
LaunchedEffect(latestId) {
    if (latestId != null) listState.animateScrollToItem(0)
}
```

Уже корректно — ничего не меняем.

- [ ] **Step 9.4: Build + install — visual smoke check**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; .\gradlew.bat :app:installDebug
```

Expected: `BUILD SUCCESSFUL`. Открыть приложение, проверить:
- Date-разделители видны (как раньше)
- Бабблы рендерятся
- Группировка same-sender работает
- (Message.System ещё нет в моке — добавим в Task 11)

- [ ] **Step 9.5: Прогнать существующие unit-тесты — никаких регрессий**

```powershell
.\gradlew.bat :core:data:testDebugUnitTest :core:ui:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 9.6: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt
git commit -m "refactor(core-ui): MessageList items() on RowItem with unified topPadding"
```

---

## Task 10: StickyDateOverlay

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt`

- [ ] **Step 10.1: Добавить private fun StickyDateOverlay**

После SystemMessageRow в MessageList.kt:

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
            kotlinx.coroutines.delay(2000)
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

Импорты которые могут отсутствовать — добавить:
```kotlin
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
// AnimatedVisibility, fadeIn, fadeOut, tween уже импортированы из animation для реакций
```

- [ ] **Step 10.2: Обернуть LazyColumn в Box, добавить StickyDateOverlay**

Текущая структура:
```kotlin
LazyColumn(modifier = modifier, ...) { items(reversed, ...) { ... } }
```

Заменить на:

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

(Импорт `androidx.compose.foundation.layout.fillMaxSize` — может уже быть. Если нет — добавить.)

- [ ] **Step 10.3: Build + install + visual check на устройстве**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; .\gradlew.bat :app:installDebug
```

Visual check на устройстве:
- Открыть чат с историей разных дней (например, chat-09 — 2 дня) или chat-12 (3 дня)
- Начать скроллить — должна появиться sticky chip сверху с датой
- Прекратить скроллить — sticky должен фейдиться через 2с
- При scroll back — sticky появляется снова
- При первом открытии чата — sticky НЕ должен показываться

- [ ] **Step 10.4: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt
git commit -m "feat(core-ui): StickyDateOverlay over MessageList with 2s idle fade"
```

---

## Task 11: Mock data — Joined examples в группах

**Files:**
- Create: `app/src/main/assets/mock/messages/grp-01.json`
- Create: `app/src/main/assets/mock/messages/grp-06.json`
- Create: `app/src/main/assets/mock/messages/grp-12.json`

- [ ] **Step 11.1: Создать grp-01.json — Конференция, последние сообщения + Joined в начале**

Из `chats.json` для grp-01: preview "Отправила материалы для итогового отчёта" от Marии (u-01, senderName="Мария Климова"), timestamp 1778685240000, ownStatus NONE.

Создать `app/src/main/assets/mock/messages/grp-01.json`:

```json
[
  {"type": "system", "id": "s-1", "chatId": "grp-01", "senderId": "u-03", "timestamp": 1778600000000,
   "isMine": false, "status": "NONE", "reactions": [], "replyTo": null,
   "kind": "Joined"},
  {"type": "text", "id": "m-1", "chatId": "grp-01", "senderId": "u-03", "timestamp": 1778601000000,
   "isMine": false, "status": "NONE", "reactions": [], "replyTo": null,
   "body": "Всем привет! Только присоединилась.", "formatting": []},
  {"type": "text", "id": "m-2", "chatId": "grp-01", "senderId": "u-01", "timestamp": 1778601600000,
   "isMine": false, "status": "NONE", "reactions": [], "replyTo": null,
   "body": "Екатерина, добро пожаловать!", "formatting": []},
  {"type": "text", "id": "m-3", "chatId": "grp-01", "senderId": "u-self", "timestamp": 1778680000000,
   "isMine": true, "status": "READ", "reactions": [], "replyTo": null,
   "body": "Когда ждать материалы по Q1?", "formatting": []},
  {"type": "text", "id": "m-4", "chatId": "grp-01", "senderId": "u-01", "timestamp": 1778685240000,
   "isMine": false, "status": "NONE", "reactions": [], "replyTo": null,
   "body": "Отправила материалы для итогового отчёта", "formatting": []}
]
```

**Замечание про last-msg:** preview-text/timestamp матчит m-4 → автосинтез в `appendIncomingPreviewIfNeeded` НЕ сработает. ✓

- [ ] **Step 11.2: Создать grp-06.json — Дизайн-команда, Joined**

Из chats.json для grp-06: preview "Готовы новые макеты профиля, посмотрите в Figma." от Софьи (u-04), ts 1778658660000, ownStatus NONE.

`app/src/main/assets/mock/messages/grp-06.json`:

```json
[
  {"type": "system", "id": "s-1", "chatId": "grp-06", "senderId": "u-07", "timestamp": 1778500000000,
   "isMine": false, "status": "NONE", "reactions": [], "replyTo": null,
   "kind": "Joined"},
  {"type": "text", "id": "m-1", "chatId": "grp-06", "senderId": "u-04", "timestamp": 1778501000000,
   "isMine": false, "status": "NONE", "reactions": [], "replyTo": null,
   "body": "Ирина, рады тебе. QA-чек по дизайну будет твоим.", "formatting": []},
  {"type": "text", "id": "m-2", "chatId": "grp-06", "senderId": "u-07", "timestamp": 1778501600000,
   "isMine": false, "status": "NONE", "reactions": [], "replyTo": null,
   "body": "Поняла, спасибо! Где смотреть макеты?", "formatting": []},
  {"type": "text", "id": "m-3", "chatId": "grp-06", "senderId": "u-04", "timestamp": 1778658660000,
   "isMine": false, "status": "NONE", "reactions": [], "replyTo": null,
   "body": "Готовы новые макеты профиля, посмотрите в Figma.", "formatting": []}
]
```

- [ ] **Step 11.3: Создать grp-12.json — DevOps, Male Joined**

Из chats.json для grp-12: preview "Обновил пайплайн, билд стал быстрее на 18%." от Дмитрия (u-10), ts 1778136480000, ownStatus NONE.

```json
[
  {"type": "system", "id": "s-1", "chatId": "grp-12", "senderId": "u-09", "timestamp": 1778050000000,
   "isMine": false, "status": "NONE", "reactions": [], "replyTo": null,
   "kind": "Joined"},
  {"type": "text", "id": "m-1", "chatId": "grp-12", "senderId": "u-10", "timestamp": 1778051000000,
   "isMine": false, "status": "NONE", "reactions": [], "replyTo": null,
   "body": "Павел, привет. Помоги, пожалуйста, с настройкой Kafka на стейдже.", "formatting": []},
  {"type": "text", "id": "m-2", "chatId": "grp-12", "senderId": "u-09", "timestamp": 1778051600000,
   "isMine": false, "status": "NONE", "reactions": [], "replyTo": null,
   "body": "Готов. Сегодня после обеда созвонимся.", "formatting": []},
  {"type": "text", "id": "m-3", "chatId": "grp-12", "senderId": "u-10", "timestamp": 1778136480000,
   "isMine": false, "status": "NONE", "reactions": [], "replyTo": null,
   "body": "Обновил пайплайн, билд стал быстрее на 18%.", "formatting": []}
]
```

- [ ] **Step 11.4: Build + install + visual verify**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; .\gradlew.bat :app:installDebug
```

Открыть приложение, найти каждый из 3 чатов:
- grp-01 (Конференция): system-row "Екатерина Орлова присоединилась к группе" + диалог + dates
- grp-06 (Дизайн-команда): system-row "Ирина Соловьёва присоединилась к группе" + диалог
- grp-12 (DevOps): system-row "Павел Сердюков присоединился к группе" + диалог

Проверить:
- Текст системного — корректный gender suffix
- Spacing — 8dp от соседних бабблов
- Стиль — centered pill, как date separator
- Скролл, sticky-дата работает

- [ ] **Step 11.5: Commit**

```bash
git add app/src/main/assets/mock/messages/grp-01.json app/src/main/assets/mock/messages/grp-06.json app/src/main/assets/mock/messages/grp-12.json
git commit -m "feat(mock): add Joined system messages to 3 group chats"
```

---

## Task 12: Документация и memory

**Files:**
- Modify: `CLAUDE.md`
- Modify (local, no commit): `~/.claude/projects/.../memory/MEMORY.md`
- Modify (local, no commit): `~/.claude/projects/.../memory/project_date_separator_temp.md` → rename `project_system_message_row_temp.md`

- [ ] **Step 12.1: Обновить CLAUDE.md — добавить про system messages**

В `CLAUDE.md` в раздел «Bubbles (MessageList)» добавить буллет после описания grouping:

```markdown
- **Системные сообщения (Joined и date-разделители)**: рендерятся как centered pill (`SystemMessageRow`, TEMP до DS-компонента). Унифицированы в `RowItem.SystemRow`. Group-spacing — 4dp внутри подряд идущих system, 8dp между группой system и соседними бабблами (см. `computeTopPadding`). Same-sender bubble grouping разрывается system-row'ом — два MY-сообщения с system между ними рендерятся как два независимых баббла (полные углы, имя+аватар на каждом). Sticky-date overlay в `MessageList` показывает дату текущей видимой top-area при скролле, фейдится через 2с idle.
```

- [ ] **Step 12.2: Update memory — переименовать temp-файл и обновить содержимое**

Это локально (не в repo), сделаю руками без коммита.

- [ ] **Step 12.3: Commit CLAUDE.md**

```bash
git add CLAUDE.md
git commit -m "docs(claude-md): system messages and sticky-date in MessageList section"
```

---

## Self-Review (final)

Перед завершением проверить:

- [ ] **Spec coverage:** Каждая секция spec'а (Data model / UI row model / Grouping / Rendering / Sticky / Tests) покрыта задачей. ✓
- [ ] **Placeholder scan:** Нет TBD/TODO в коде/тестах. ✓
- [ ] **Type consistency:** `RowItem.SystemRow.isDate: Boolean`, key format `"date-$dayKey"` / `"sys-${msg.id}"` — используются согласованно во всех тасках. ✓
- [ ] **Спека → план:** Тесты упомянутые в spec'е (FlattenToRowsTest / ComputeGroupPositionsTest / ComputeTopPaddingTest) → реализованы как методы в одном `MessageRowsTest.kt`. ✓
