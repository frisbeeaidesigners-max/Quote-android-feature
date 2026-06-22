# Message Bubble Context Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить overlay-меню по тапу на bubble в ChatDetailScreen с быстрыми реакциями (5 эмодзи + «+») и пунктами действий (7 для чужих, 9 для своих с «Удалить» красным). Тап по эмодзи добавляет ReactionsView-стэк под бабблом. «Копировать» и «Удалить» работают; остальные пункты — закрывают меню.

**Architecture:** Box-overlay на уровне `ChatDetailScreen` с `AnimatedVisibility` (fade+slide-up). Stateless `MessageContextMenu(state, lambdas)` принимает события; единственный владелец state — `ChatDetailViewModel`. Reactions/menu пока живут в `:core:ui/contextmenu/` как временное решение, потом мигрируют в `:components`. `:components` (android-components) **не трогаем** по CLAUDE.md.

**Tech Stack:** Kotlin 1.9.22, Compose BOM 2024.02.00, kotlinx-serialization, JUnit для data-tests. Build через Android Studio JBR (`C:\Program Files\Android\Android Studio\jbr`).

**Spec:** `docs/superpowers/specs/2026-05-18-message-bubble-context-menu-design.md`

**Build/test environment (Windows PowerShell):**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

**ADB path:** `C:\Users\Grond\AppData\Local\Android\Sdk\platform-tools\adb.exe`

---

## File structure

**New files:**
- `core/model/src/main/java/com/example/template/core/model/ReactionStack.kt`
- `core/ui/src/main/java/com/example/template/core/ui/contextmenu/MessageMenuModel.kt`
- `core/ui/src/main/java/com/example/template/core/ui/contextmenu/ReactionsPicker.kt`
- `core/ui/src/main/java/com/example/template/core/ui/contextmenu/MenuItemsCard.kt`
- `core/ui/src/main/java/com/example/template/core/ui/contextmenu/MessageContextMenu.kt`
- `core/data/src/test/java/com/example/template/core/data/ReactionsTest.kt`
- `core/data/src/test/java/com/example/template/core/data/DeleteMessageTest.kt`

**Modified files:**
- `core/model/src/main/java/com/example/template/core/model/Message.kt` — abstract `reactions` + поле в каждом подтипе
- `core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt` — два новых метода интерфейса
- `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt` — реализация
- `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt` — `onBubbleTap` + `onReactionTap` параметры, Column-обёртка с `clickable`, рендер `ReactionsView` под бабблом
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt` — `ContextMenuState` flow + actions
- `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt` — Box-overlay + `BackHandler` + проброс onBubbleTap/onReactionTap в MessageList

---

## Icon name mapping

Сверено с `app/src/main/assets/icons/` (после `preBuild` sync из `icons-library`):

| MenuItem | Icon asset |
|---|---|
| Reply | `reply` |
| Edit | `edit` |
| Forward | `forward` |
| Pin | `pin` |
| Copy | `copy` |
| AddTag | `tag-flag` |
| Save | `bookmark` |
| Select | `check-outline` |
| Delete | `delete` |
| Add reaction («+») | `plus` |

---

### Task 1: Создать `ReactionStack` модель

**Files:**
- Create: `core/model/src/main/java/com/example/template/core/model/ReactionStack.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.template.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ReactionStack(
    val emoji: String,
    val count: Int = 1,
    val isMine: Boolean = false,
)
```

- [ ] **Step 2: Скомпилировать модуль**

Run: `.\gradlew.bat :core:model:compileKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```powershell
git add core/model/src/main/java/com/example/template/core/model/ReactionStack.kt
git commit -m "feat(model): ReactionStack data class"
```

---

### Task 2: Добавить `reactions` поле в `Message`

**Files:**
- Modify: `core/model/src/main/java/com/example/template/core/model/Message.kt`

- [ ] **Step 1: Открыть `Message.kt` и зафиксировать текущую структуру**

В sealed class `Message` уже объявлены abstract `id`, `chatId`, `senderId`, `timestamp`, `isMine`, `status`. Каждый подтип (`Text`, `Media`, `Voice`, `Link`, `CallMeet`) — отдельный `data class`. Нужно добавить:

```kotlin
abstract val reactions: List<ReactionStack>
```

в sealed class, и в каждый подтип добавить параметр:

```kotlin
override val reactions: List<ReactionStack> = emptyList(),
```

в конец конструктора (после существующих полей). Это сохраняет обратную совместимость JSON: старые файлы без поля `reactions` парсятся, default — пустой список.

- [ ] **Step 2: Применить изменение**

Добавь импорт (если ещё нет):

```kotlin
import com.example.template.core.model.ReactionStack
```

(если файл уже в пакете `com.example.template.core.model` — импорт не нужен.)

В sealed class:

```kotlin
abstract val reactions: List<ReactionStack>
```

В каждом подтипе (Text/Media/Voice/Link/CallMeet) — последним параметром:

```kotlin
override val reactions: List<ReactionStack> = emptyList(),
```

- [ ] **Step 3: Прогнать существующие тесты — убедиться что JSON парсится**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :core:data:testDebugUnitTest --console=plain
```
Expected: `BUILD SUCCESSFUL`, `JsonParsingTest` зелёный — это и есть проверка обратной совместимости JSON-моков.

- [ ] **Step 4: Commit**

```powershell
git add core/model/src/main/java/com/example/template/core/model/Message.kt
git commit -m "feat(model): Message.reactions field with default emptyList"
```

---

### Task 3: Расширить `MessengerRepository` интерфейс

**Files:**
- Modify: `core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt`

- [ ] **Step 1: Добавить два метода**

В интерфейсе после `sendMediaMessage`:

```kotlin
suspend fun toggleReaction(chatId: String, messageId: String, emoji: String)
suspend fun deleteMessage(chatId: String, messageId: String)
```

- [ ] **Step 2: Скомпилировать**

Run: `.\gradlew.bat :core:data:compileDebugKotlin --console=plain`
Expected: **FAIL** — `MockRepositoryImpl` не реализует новые методы. Это ожидаемо; следующая task их добавляет.

- [ ] **Step 3: НЕ коммитим пока — будет вместе с реализацией в task 4**

---

### Task 4: Реализовать `toggleReaction` в `MockRepositoryImpl` (TDD)

**Files:**
- Modify: `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt`
- Create: `core/data/src/test/java/com/example/template/core/data/ReactionsTest.kt`

- [ ] **Step 1: Написать failing test**

Create `core/data/src/test/java/com/example/template/core/data/ReactionsTest.kt`:

```kotlin
package com.example.template.core.data

import com.example.template.core.model.ReactionStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReactionsTest {

    @Test
    fun `toggleReaction adds new stack when emoji absent`() {
        val before = emptyList<ReactionStack>()
        val after = applyToggle(before, "❤️")
        assertEquals(listOf(ReactionStack("❤️", count = 1, isMine = true)), after)
    }

    @Test
    fun `toggleReaction increments when emoji exists and not mine`() {
        val before = listOf(ReactionStack("👍", count = 3, isMine = false))
        val after = applyToggle(before, "👍")
        assertEquals(listOf(ReactionStack("👍", count = 4, isMine = true)), after)
    }

    @Test
    fun `toggleReaction decrements when emoji is mine`() {
        val before = listOf(ReactionStack("🎉", count = 2, isMine = true))
        val after = applyToggle(before, "🎉")
        assertEquals(listOf(ReactionStack("🎉", count = 1, isMine = false)), after)
    }

    @Test
    fun `toggleReaction removes stack when last mine count drops to zero`() {
        val before = listOf(ReactionStack("🥳", count = 1, isMine = true))
        val after = applyToggle(before, "🥳")
        assertEquals(emptyList<ReactionStack>(), after)
    }

    @Test
    fun `toggleReaction preserves order of other reactions`() {
        val before = listOf(
            ReactionStack("👌", count = 1, isMine = false),
            ReactionStack("👍", count = 2, isMine = false),
            ReactionStack("❤️", count = 5, isMine = false),
        )
        val after = applyToggle(before, "👍")
        assertEquals(
            listOf(
                ReactionStack("👌", count = 1, isMine = false),
                ReactionStack("👍", count = 3, isMine = true),
                ReactionStack("❤️", count = 5, isMine = false),
            ),
            after,
        )
    }

    private fun applyToggle(reactions: List<ReactionStack>, emoji: String): List<ReactionStack> =
        MockRepositoryImpl.toggleReactionList(reactions, emoji)
}
```

- [ ] **Step 2: Запустить тест — убедиться что не компилируется**

Run:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :core:data:testDebugUnitTest --console=plain
```
Expected: FAIL — `MockRepositoryImpl.toggleReactionList` не существует.

- [ ] **Step 3: Реализовать `toggleReactionList` в companion + публичный метод `toggleReaction`**

В `MockRepositoryImpl.kt` в companion object добавить:

```kotlin
// Toggle-логика реакции, изолированная для unit-тестов:
//   нет такого emoji          → добавить ReactionStack(emoji, 1, isMine=true)
//   есть, isMine=false        → count+1, isMine=true
//   есть, isMine=true, count>1 → count-1, isMine=false
//   есть, isMine=true, count==1 → удалить
fun toggleReactionList(reactions: List<ReactionStack>, emoji: String): List<ReactionStack> {
    val existing = reactions.firstOrNull { it.emoji == emoji }
    return when {
        existing == null -> reactions + ReactionStack(emoji = emoji, count = 1, isMine = true)
        existing.isMine && existing.count <= 1 -> reactions.filterNot { it.emoji == emoji }
        existing.isMine -> reactions.map {
            if (it.emoji == emoji) it.copy(count = it.count - 1, isMine = false) else it
        }
        else -> reactions.map {
            if (it.emoji == emoji) it.copy(count = it.count + 1, isMine = true) else it
        }
    }
}
```

В теле класса добавить override:

```kotlin
override suspend fun toggleReaction(chatId: String, messageId: String, emoji: String) {
    val cache = messageCache[chatId] ?: return
    val current = cache.value.firstOrNull { it.id == messageId } ?: return
    val updated = updateReactions(current, toggleReactionList(current.reactions, emoji))
    cache.value = cache.value.map { if (it.id == messageId) updated else it }
}

private fun updateReactions(msg: Message, reactions: List<ReactionStack>): Message = when (msg) {
    is Message.Text -> msg.copy(reactions = reactions)
    is Message.Media -> msg.copy(reactions = reactions)
    is Message.Voice -> msg.copy(reactions = reactions)
    is Message.Link -> msg.copy(reactions = reactions)
    is Message.CallMeet -> msg.copy(reactions = reactions)
}
```

Добавить импорт (если нужно): `import com.example.template.core.model.ReactionStack`.

- [ ] **Step 4: Прогнать тесты**

Run:
```powershell
.\gradlew.bat :core:data:testDebugUnitTest --console=plain
```
Expected: `BUILD SUCCESSFUL`, все 5 тестов `ReactionsTest` зелёные + старые тесты тоже.

- [ ] **Step 5: Commit**

```powershell
git add core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt `
        core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt `
        core/data/src/test/java/com/example/template/core/data/ReactionsTest.kt
git commit -m "feat(data): toggleReaction + unit tests"
```

---

### Task 5: Реализовать `deleteMessage` (TDD)

**Files:**
- Modify: `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt`
- Create: `core/data/src/test/java/com/example/template/core/data/DeleteMessageTest.kt`

- [ ] **Step 1: Написать failing test**

Create `core/data/src/test/java/com/example/template/core/data/DeleteMessageTest.kt`:

```kotlin
package com.example.template.core.data

import com.example.template.core.model.Message
import com.example.template.core.model.MessagePreview
import com.example.template.core.model.MessageStatus
import com.example.template.core.model.PreviewKind
import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteMessageTest {

    @Test
    fun `previewFromLast falls back to empty when list is empty`() {
        val preview = MockRepositoryImpl.previewFromLast(emptyList())
        assertEquals(
            MessagePreview(text = "", kind = PreviewKind.Text, timestamp = 0L, ownStatus = MessageStatus.NONE),
            preview,
        )
    }

    @Test
    fun `previewFromLast uses last Text message body`() {
        val msgs = listOf<Message>(
            Message.Text(
                id = "m1", chatId = "c", senderId = "u1", timestamp = 100L,
                isMine = false, status = MessageStatus.NONE, body = "old",
            ),
            Message.Text(
                id = "m2", chatId = "c", senderId = "u1", timestamp = 200L,
                isMine = true, status = MessageStatus.DELIVERED, body = "newest",
            ),
        )
        val preview = MockRepositoryImpl.previewFromLast(msgs)
        assertEquals("newest", preview.text)
        assertEquals(200L, preview.timestamp)
        assertEquals(MessageStatus.DELIVERED, preview.ownStatus)
    }
}
```

- [ ] **Step 2: Запустить тест — FAIL ожидаем**

Run: `.\gradlew.bat :core:data:testDebugUnitTest --console=plain`
Expected: FAIL — `previewFromLast` не существует.

- [ ] **Step 3: Реализовать**

В `MockRepositoryImpl.kt` companion object:

```kotlin
fun previewFromLast(messages: List<Message>): MessagePreview {
    val last = messages.lastOrNull()
        ?: return MessagePreview(
            text = "",
            kind = PreviewKind.Text,
            timestamp = 0L,
            ownStatus = MessageStatus.NONE,
        )
    return when (last) {
        is Message.Text -> MessagePreview(
            text = last.body,
            kind = PreviewKind.Text,
            timestamp = last.timestamp,
            ownStatus = last.status,
        )
        is Message.Media -> MessagePreview(
            text = last.caption?.takeIf { it.isNotBlank() } ?: when {
                last.attachments.any { it.type == AttachmentType.Video } -> "Видео"
                else -> "Изображение"
            },
            kind = PreviewKind.Text,
            timestamp = last.timestamp,
            ownStatus = last.status,
        )
        // Voice/Link/CallMeet: ChatList сейчас рассчитан только на Text preview;
        // используем пустую строку чтобы не врать (это прототип, пользователь увидит чат без preview).
        else -> MessagePreview(
            text = "",
            kind = PreviewKind.Text,
            timestamp = last.timestamp,
            ownStatus = MessageStatus.NONE,
        )
    }
}
```

В теле класса добавить override:

```kotlin
override suspend fun deleteMessage(chatId: String, messageId: String) {
    val cache = messageCache[chatId] ?: return
    val newList = cache.value.filterNot { it.id == messageId }
    if (newList.size == cache.value.size) return  // не нашли — выходим
    cache.value = newList
    _chats.value = _chats.value.map { chat ->
        if (chat.id == chatId) chat.copy(lastMessage = previewFromLast(newList)) else chat
    }
}
```

- [ ] **Step 4: Прогнать тесты**

Run: `.\gradlew.bat :core:data:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, все тесты зелёные.

- [ ] **Step 5: Commit**

```powershell
git add core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt `
        core/data/src/test/java/com/example/template/core/data/DeleteMessageTest.kt
git commit -m "feat(data): deleteMessage + lastMessage recompute"
```

---

### Task 6: `MenuItem` enum + тест порядка пунктов

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/contextmenu/MessageMenuModel.kt`
- Create: `core/ui/src/test/java/com/example/template/core/ui/contextmenu/MenuItemOrderTest.kt`

Тест выносим в `:core:ui` test sources. Если test source set ещё не существует — Gradle создаст его при первом запуске.

- [ ] **Step 1: Создать `MessageMenuModel.kt`**

```kotlin
package com.example.template.core.ui.contextmenu

// Временное решение для prototype-меню; после миграции в :components — переедет туда.
// TODO: перенести вместе с MessageContextMenu/ReactionsPicker/MenuItemsCard в :components.

enum class MenuItem(val label: String, val iconName: String, val destructive: Boolean = false) {
    Reply("Ответить", "reply"),
    Edit("Редактировать", "edit"),
    Forward("Переслать", "forward"),
    Pin("Закрепить", "pin"),
    Copy("Копировать", "copy"),
    AddTag("Добавить метку", "tag-flag"),
    Save("В сохранённое", "bookmark"),
    Select("Выбрать", "check-outline"),
    Delete("Удалить", "delete", destructive = true);

    companion object {
        fun forMessage(isMine: Boolean): List<MenuItem> =
            if (isMine) listOf(Reply, Edit, Forward, Pin, Copy, AddTag, Save, Select, Delete)
            else        listOf(Reply, Forward, Pin, Copy, AddTag, Save, Select)
    }
}

data class ContextMenuState(
    val messageId: String,
    val isMine: Boolean,
    val canCopy: Boolean,
    val copyText: String,
)
```

- [ ] **Step 2: Скомпилировать**

Run: `.\gradlew.bat :core:ui:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add core/ui/src/main/java/com/example/template/core/ui/contextmenu/MessageMenuModel.kt
git commit -m "feat(contextmenu): MenuItem enum + ContextMenuState (temporary, will migrate to :components)"
```

---

### Task 7: `ReactionsPicker` Composable

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/contextmenu/ReactionsPicker.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.template.core.ui.contextmenu

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.components.R as ComponentsR
import com.example.components.button.ButtonColorScheme
import com.example.components.button.ButtonView
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark

// Временный компонент; после миграции в :components — переедет туда.
// TODO: вынести вместе с MessageContextMenu/MenuItemsCard.

@Composable
fun ReactionsPicker(
    emojis: List<String>,
    onReactionTap: (String) -> Unit,
    onAddReactionTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val ctx = LocalContext.current
    val cardBg = remember(brand, isDark) { Color(brand.bubblesColorScheme(isDark).messageScreenBackground) }
    // Карточка с реакциями — фон контрастный к dim 50%. Использую messageScreenBackground как нейтральный
    // светлый/тёмный sheet, согласованный с фоном чата.

    val secondaryScheme = remember(brand, isDark) {
        val base = brand.secondaryButtonColorScheme(isDark)
        // Внутри Figma макета цвет иконки «+» — Basic Color 55 (8C000000 / 8CFFFFFF). DSColors Kotlin
        // API не выводит basic_55, обращаюсь к XML-ресурсу напрямую.
        base.copy(iconContentColor = ContextCompat.getColor(ctx, ComponentsR.color.ds_basic_55))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        emojis.forEach { emoji ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onReactionTap(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = 24.sp)
            }
        }
        AndroidView(
            factory = { context ->
                ButtonView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    onClick = { onAddReactionTap() }
                }
            },
            update = { view ->
                view.configure(
                    iconName = "plus",
                    size = ButtonView.Size.S,
                    filled = true,
                    colorScheme = secondaryScheme,
                )
                view.onClick = { onAddReactionTap() }
            },
        )
    }
}
```

Если у `DSBrand` нет метода `secondaryButtonColorScheme(isDark)` — проверить точное имя в `DSBrand.kt`. Альтернативное имя: `secondaryButtonScheme` / `buttonSecondaryColorScheme` — подобрать ближайшее и оставить TODO.

- [ ] **Step 2: Скомпилировать**

Run: `.\gradlew.bat :core:ui:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`. Если ошибка по `secondaryButtonColorScheme` — открыть `DSBrand.kt` в `../android-components/components/src/main/java/com/example/components/designsystem/`, найти точное имя метода и подставить.

- [ ] **Step 3: Commit**

```powershell
git add core/ui/src/main/java/com/example/template/core/ui/contextmenu/ReactionsPicker.kt
git commit -m "feat(contextmenu): ReactionsPicker (5 emojis + ButtonView '+' Secondary S Filled, basic_55 icon)"
```

---

### Task 8: `MenuItemsCard` Composable

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/contextmenu/MenuItemsCard.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.template.core.ui.contextmenu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.components.designsystem.DSColors
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark
import com.example.template.core.ui.iconPainterOrNull

// Временный компонент; после миграции в :components — переедет туда.

@Composable
fun MenuItemsCard(
    items: List<MenuItem>,
    onItemTap: (MenuItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val cardBg = remember(brand, isDark) { Color(brand.bubblesColorScheme(isDark).messageScreenBackground) }
    val primaryText = remember(ctx) { Color(DSColors.textPrimary(ctx)) }
    val dangerColor = remember(ctx) { Color(DSColors.danger(ctx)) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg),
    ) {
        items.forEach { item ->
            val color = if (item.destructive) dangerColor else primaryText
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onItemTap(item) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val painter: Painter? = iconPainterOrNull(ctx, item.iconName, 24.dp)
                if (painter != null) {
                    androidx.compose.foundation.Image(
                        painter = painter,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(color),
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(text = item.label, color = color)
            }
        }
    }
}
```

- [ ] **Step 2: Проверить наличие helper'а `iconPainterOrNull`**

Поискать в `core/ui/src/main/`:
```powershell
.\gradlew.bat :core:ui:compileDebugKotlin --console=plain
```
Если компиляция падает с `unresolved iconPainterOrNull` — открыть `core/ui/src/main/java/com/example/template/core/ui/` и найти существующий helper для загрузки SVG из assets/icons. Если такого нет — создать утилиту в `core/ui/src/main/java/com/example/template/core/ui/IconPainter.kt`:

```kotlin
package com.example.template.core.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import com.example.components.designsystem.DSIcon

// Returns a Compose Painter for an SVG icon from app/src/main/assets/icons/<name>.svg,
// rasterized at the given size. Returns null if the icon is missing.
@Composable
fun iconPainterOrNull(ctx: Context, name: String, size: Dp): Painter? {
    val density = LocalDensity.current
    return remember(ctx, name, size) {
        val drawable = DSIcon.named(ctx, name, with(density) { size.toPx() }) ?: return@remember null
        // DSIcon returns a Drawable; convert to Bitmap → BitmapPainter so Compose can render it.
        val sizePx = with(density) { size.roundToPx() }
        val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        BitmapPainter(bitmap.asImageBitmap())
    }
}
```

- [ ] **Step 3: Скомпилировать**

Run: `.\gradlew.bat :core:ui:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```powershell
git add core/ui/src/main/java/com/example/template/core/ui/contextmenu/MenuItemsCard.kt `
        core/ui/src/main/java/com/example/template/core/ui/IconPainter.kt
git commit -m "feat(contextmenu): MenuItemsCard with icon + destructive color tint"
```

(Если `IconPainter.kt` не создавался — убрать из `git add`.)

---

### Task 9: `MessageContextMenu` (assembly)

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/contextmenu/MessageContextMenu.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.template.core.ui.contextmenu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Stateless overlay-меню. Принимает state + lambdas; владелец state — ChatDetailViewModel.
// TODO: вместе с ReactionsPicker/MenuItemsCard перенести в :components.

private val DEFAULT_EMOJIS = listOf("👌", "👍", "🎉", "🥳", "❤️")

@Composable
fun MessageContextMenu(
    state: ContextMenuState,
    onReactionTap: (emoji: String) -> Unit,
    onAddReactionTap: () -> Unit,
    onMenuItemTap: (MenuItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    emojis: List<String> = DEFAULT_EMOJIS,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            ReactionsPicker(
                emojis = emojis,
                onReactionTap = onReactionTap,
                onAddReactionTap = onAddReactionTap,
            )
            Spacer(Modifier.height(8.dp))
            MenuItemsCard(
                items = MenuItem.forMessage(state.isMine),
                onItemTap = onMenuItemTap,
            )
        }
    }
}
```

- [ ] **Step 2: Скомпилировать**

Run: `.\gradlew.bat :core:ui:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add core/ui/src/main/java/com/example/template/core/ui/contextmenu/MessageContextMenu.kt
git commit -m "feat(contextmenu): MessageContextMenu overlay assembly"
```

---

### Task 10: Расширить `ChatDetailViewModel`

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt`

- [ ] **Step 1: Добавить state + actions**

В `ChatDetailViewModel.kt`:

Импорты:
```kotlin
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.example.template.core.ui.contextmenu.ContextMenuState
import com.example.template.core.ui.contextmenu.MenuItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
```

В теле класса (после существующих полей):

```kotlin
private val _contextMenu = MutableStateFlow<ContextMenuState?>(null)
val contextMenu: StateFlow<ContextMenuState?> = _contextMenu.asStateFlow()

fun openContextMenu(messageId: String) {
    val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
    _contextMenu.value = ContextMenuState(
        messageId = msg.id,
        isMine = msg.isMine,
        canCopy = canCopy(msg),
        copyText = copyTextFor(msg),
    )
}

fun dismissContextMenu() { _contextMenu.value = null }

fun toggleReaction(emoji: String) {
    val st = _contextMenu.value ?: return
    viewModelScope.launch { repository.toggleReaction(chatId, st.messageId, emoji) }
    dismissContextMenu()
}

/** Прямой toggle минуя overlay — для тапов по уже выбранной реакции под бабблом. */
fun toggleReactionDirect(messageId: String, emoji: String) {
    viewModelScope.launch { repository.toggleReaction(chatId, messageId, emoji) }
}

fun onMenuItem(item: MenuItem, context: Context) {
    val st = _contextMenu.value ?: return
    when (item) {
        MenuItem.Copy -> if (st.canCopy && st.copyText.isNotEmpty()) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("message", st.copyText))
        }
        MenuItem.Delete -> if (st.isMine) {
            viewModelScope.launch { repository.deleteMessage(chatId, st.messageId) }
        }
        else -> Unit
    }
    dismissContextMenu()
}

private fun canCopy(msg: com.example.template.core.model.Message): Boolean = when (msg) {
    is com.example.template.core.model.Message.Text -> msg.body.isNotEmpty()
    is com.example.template.core.model.Message.Media -> !msg.caption.isNullOrEmpty()
    else -> false
}

private fun copyTextFor(msg: com.example.template.core.model.Message): String = when (msg) {
    is com.example.template.core.model.Message.Text -> msg.body
    is com.example.template.core.model.Message.Media -> msg.caption.orEmpty()
    else -> ""
}
```

- [ ] **Step 2: Скомпилировать**

Run: `.\gradlew.bat :feature:chatdetail:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt
git commit -m "feat(chatdetail): ContextMenuState + open/dismiss/toggleReaction/onMenuItem actions"
```

---

### Task 11: Расширить `MessageList` — onBubbleTap + рендер реакций под бабблом

**Files:**
- Modify: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt`

- [ ] **Step 1: Расширить сигнатуру**

Заменить блок параметров `MessageList`:

```kotlin
@Composable
fun MessageList(
    messages: List<Message>,
    modifier: Modifier = Modifier,
    personaByUserId: (String) -> Persona? = { null },
    onBubbleTap: (messageId: String) -> Unit = {},
    onReactionTap: (messageId: String, emoji: String) -> Unit = { _, _ -> },
) {
```

Добавить импорты (если их ещё нет):

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import com.example.components.reactions.ReactionsView
```

- [ ] **Step 2: Surgical edits — убрать `padding(top = topPadding)` из каждой AndroidView ветки `when (msg)`**

В существующих 5 ветках `is Message.Text`, `is Message.Media`, `is Message.Voice`, `is Message.Link`, `is Message.CallMeet` найти `Modifier.fillMaxWidth().padding(top = topPadding)` (или вариант `Modifier.fillMaxWidth(); .padding(top = topPadding)`) и заменить на просто `Modifier.fillMaxWidth()`. Padding перенесём на внешний Column-обёртку в следующем шаге.

Используй Edit-tool пять раз (по одному на каждую ветку) с уникальным контекстом из соответствующей ветки (например, `BubblesView(ctx)` для Text, `MediaBubbleView(ctx)` для Media и т.д.).

- [ ] **Step 3: Обернуть тело item'а в Column с clickable + добавить рендер ReactionsView**

Найти в `MessageList.kt` строку `items(reversed, key = { it.id }) { msg ->` и проследи до закрывающей `}`. Внутри текущий контент: вычисление position/topPadding/isFirstInGroup/.../avatarScheme + `when (msg) { ... }` блок.

Этот контент нужно обернуть так:

ДО: `when (msg) { ... }` стоит сразу после `val avatarScheme = ...`.
ПОСЛЕ: между ними добавляется `Column(...) {` … `when (msg) { ... }` (без padding'а на AndroidView) … `if (msg.reactions.isNotEmpty()) { AndroidView(...) }` … `}` (закрытие Column).

Конкретно — замени блок:

```kotlin
val avatarScheme = avatarSchemesByIndex[avatarSchemeIdx]
    ?: avatarSchemesByIndex.values.firstOrNull()
    ?: AvatarColorScheme.DEFAULT
when (msg) {
```

на:

```kotlin
val avatarScheme = avatarSchemesByIndex[avatarSchemeIdx]
    ?: avatarSchemesByIndex.values.firstOrNull()
    ?: AvatarColorScheme.DEFAULT
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = topPadding)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { onBubbleTap(msg.id) },
) {
    when (msg) {
```

И в конце item'а (после закрытия `when`-блока, но до закрытия `items {}`-блока) добавить рендер реакций + закрытие Column:

```kotlin
        }    // end of when (msg)
        if (msg.reactions.isNotEmpty()) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    ReactionsView(ctx).apply { layoutParams = fillWidthLayout() }
                },
                update = { view ->
                    view.configure(
                        reactions = msg.reactions.map { r -> ReactionsView.Stack(r.emoji, r.count, r.isMine) },
                        showAddButton = false,
                        colorScheme = brand.reactionsColorScheme(isDark),
                        onReactionClick = { idx -> onReactionTap(msg.id, msg.reactions[idx].emoji) },
                    )
                },
            )
        }
    }    // end of Column wrapper
```

Найти точку «end of when (msg)» — это `}`, закрывающая `when` (последняя ветка `is Message.CallMeet -> { ... }`). После неё вставить `if (...) { AndroidView(...) }`, потом ещё один `}` для Column.

- [ ] **Step 4: Скомпилировать**

Run: `.\gradlew.bat :core:ui:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Прогнать data-тесты — убедиться, что ничего не сломалось**

Run: `.\gradlew.bat :core:data:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, все тесты зелёные.

- [ ] **Step 6: Commit**

```powershell
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt
git commit -m "feat(messagelist): onBubbleTap callback + ReactionsView under bubble"
```

---

### Task 12: Wiring в `ChatDetailScreen`

**Files:**
- Modify: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt`

- [ ] **Step 1: Импорты + state collection**

Добавить импорты:
```kotlin
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import com.example.template.core.ui.contextmenu.MessageContextMenu
```

- [ ] **Step 2: Обернуть существующий Column в Box и добавить overlay**

Текущий `ChatDetailScreen` рендерит верхний Column с Header / MessageList / MessagePanel. Обернуть его в Box и добавить overlay:

```kotlin
val contextMenuState by viewModel.contextMenu.collectAsState()
val ctxLocal = LocalContext.current

Box(modifier = Modifier.fillMaxSize()) {
    Column(
        // существующие модификаторы для Column'а с background + pointerInput dismissKeyboard
    ) {
        HeaderHost(...)
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(...)  // BackgroundPatternView
            MessageList(
                messages = messages,
                modifier = Modifier.fillMaxSize(),
                personaByUserId = viewModel::personaForUser,
                onBubbleTap = viewModel::openContextMenu,
                onReactionTap = viewModel::toggleReactionDirect,
            )
        }
        MessagePanelHost(...)
    }

    AnimatedVisibility(
        visible = contextMenuState != null,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 4 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 4 },
    ) {
        contextMenuState?.let { state ->
            MessageContextMenu(
                state = state,
                onReactionTap = viewModel::toggleReaction,
                onAddReactionTap = viewModel::dismissContextMenu,
                onMenuItemTap = { item -> viewModel.onMenuItem(item, ctxLocal) },
                onDismiss = viewModel::dismissContextMenu,
            )
        }
    }
}
BackHandler(enabled = contextMenuState != null) { viewModel.dismissContextMenu() }
```

- [ ] **Step 3: Скомпилировать**

Run: `.\gradlew.bat :feature:chatdetail:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Полная сборка APK**

Run: `.\gradlew.bat :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt
git commit -m "feat(chatdetail): overlay MessageContextMenu + BackHandler dismiss"
```

---

### Task 13: Установка на устройство и acceptance-проверка

**Files:** нет

- [ ] **Step 1: Установить APK**

```powershell
$adb = "C:\Users\Grond\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "app\build\outputs\apk\debug\app-debug.apk"
```
Expected: `Success`.

- [ ] **Step 2: Acceptance-чеклист (глазами на устройстве)**

Открыть P2P-чат (например «Мария Климова» — pinned, есть hardcoded preview). Если в чате есть история — bonus, можно сразу попробовать все типы баблов. Иначе отправить пару сообщений.

Пройтись по criteria из спеки:

  1. Тап по любому баблу → overlay появляется. Чужой → 7 пунктов. Свой → 9 пунктов с красным «Удалить» внизу.
  2. Тап по эмодзи в верхнем ряду → меню закрывается; ReactionsView появляется под бабблом со счётчиком 1.
  3. Снова открыть меню на том же бабле, тап по тому же эмодзи → ReactionsView исчезает (count 0).
  4. Тап «+» в реакциях → меню закрывается, ничего не добавляется.
  5. Своё текстовое сообщение → меню → «Копировать» → в clipboard скопирован текст (проверить вставкой в MessagePanel input).
  6. Своё сообщение → меню → «Удалить» → сообщение исчезает. Если оно было последним — чат в чат-листе должен показать предыдущее сообщение как preview.
  7. Тап «Ответить» / «Переслать» / «Закрепить» и т.д. → меню закрывается, ничего больше не происходит.
  8. Тап вне карточек (на dim) → меню закрывается.
  9. Системный back при открытом меню → меню закрывается, чат остаётся.
  10. Анимация показа/закрытия — fade+slide-up, плавная, без рывков.
  11. После delete, существующий авто-скролл к новому сообщению и сортировка чат-листа после `sendText` продолжают работать (отправить новое — лента поедет вниз; в чат-листе чат поднимется).

- [ ] **Step 3: Если есть мелкие визуальные правки — поправить, пересобрать, переустановить**

Если что-то не сошлось — вернуться в соответствующий task, поправить, повторить sub-сборку этого модуля + assembleDebug + install. Закоммитить отдельным `fix(contextmenu): <что>`.

- [ ] **Step 4: Финальный статус-commit (если ничего не правилось)**

Ничего коммитить не надо — все правки уже в коммитах task'ов 1-12.

---

## Acceptance criteria → tasks coverage

| Spec criteria | Tasks |
|---|---|
| 1. Тап по баблу → overlay | T11 (onBubbleTap), T12 (overlay) |
| 2. Чужое 7 пунктов / Своё 9 с «Удалить» красным | T6 (MenuItem.forMessage), T8 (destructive tint) |
| 3. Эмодзи toggle + ReactionsView под бабблом | T4 (toggle logic), T11 (render under bubble) |
| 4. «+» no-op | T7 (onAddReactionTap → dismiss в T12) |
| 5. Копировать в clipboard | T10 (onMenuItem Copy) |
| 6. Удалить сообщение + recompute lastMessage | T5 (deleteMessage + previewFromLast) |
| 7. Остальные пункты — no-op + dismiss | T10 (when else -> Unit + dismissContextMenu) |
| 8. Layout dim 50%, 8dp gap, 16dp / 24dp padding | T9 (MessageContextMenu) |
| 9. Back + tap-on-dim dismiss | T9 (dim clickable), T12 (BackHandler) |
| 10. Анимация ~220ms / ~160ms | T12 (AnimatedVisibility enter/exit) |
| 11. Существующее не сломалось | T13 (manual verify) + data-тесты в T2/T4/T5 |
