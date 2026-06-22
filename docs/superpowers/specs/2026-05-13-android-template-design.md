# android-template — Design Spec

**Date:** 2026-05-13
**Status:** Approved (brainstorming)
**Author:** Brainstorming session
**Related repos:** `android-components`, `icons-library`

---

## 1. Цель и контекст

`android-template` — Android-приложение-каркас корпоративного мессенджера. Назначение: интерактивный прототип, на базе которого дизайн-команда тестирует гипотезы (UX, визуал, новые паттерны). Backend и БД не планируются.

**Что входит в каркас:**
- 4 root-экрана (вкладки bottom tabs): Чаты, Пространства, Звонки, Профиль
- Детальный экран чата (P2P / Group / Channel)
- Локальное хранилище mock-данных в JSON
- Базовая отправка текстовых сообщений (только UI, без персистентности)

**Что НЕ входит в первую версию:**
- Сетевой backend, БД
- Создание чатов, групп, каналов
- Реакции на лету, edit, reply, голосовые сообщения (отправка), media-вложения (отправка)
- Реальное воспроизведение audio / video / push
- Login / onboarding / settings экраны
- Search overlay (UI бара показывается, но реализация поиска вне scope)

**Принципы:**
- Все компоненты — из `android-components` через Gradle composite build
- Иконки и цветовые токены — из `icons-library` (через цепочку: `icons-library` → `android-components` → `android-template`)
- Дисциплина: никаких хардкод-цветов, хардкод-иконок, хардкод-шрифтов вне дизайн-системы
- Никаких внутренних enum-имён `DSBrand` в исходниках android-template — только NATO-кодовые имена `foxtrot/tango/sierra/kilo/love`. Конвертация — через `DSBrand.byCodename(...)`

---

## 2. Стек и совместимость

Совпадает с `android-components`:

| Технология | Версия |
|-----------|--------|
| Kotlin | 1.9.22 |
| AGP | 8.2.2 |
| Compose BOM | 2024.02.00 |
| Compose Compiler | 1.5.10 |
| compileSdk | 34 |
| minSdk | 26 |
| JDK | 17 (Android Studio JBR) |
| Material | 3 |
| Navigation | androidx.navigation:navigation-compose |
| Serialization | kotlinx-serialization-json |

Версии библиотек централизованы в `gradle/libs.versions.toml` (Version Catalog). Общая логика модулей — через convention plugins в `build-logic/` (или `buildSrc/`): `android-library-convention`, `android-feature-convention`, `android-application-convention`, `kotlin-library-convention`.

---

## 3. Модульная структура

```
android-template/
├── app/                                  com.android.application
├── core/
│   ├── model/                            com.android.library (или kotlin("jvm"))
│   ├── data/                             com.android.library
│   ├── ui/                               com.android.library
│   └── navigation/                       com.android.library
├── feature/
│   ├── chats/                            com.android.library
│   ├── spaces/                           com.android.library
│   ├── calls/                            com.android.library
│   ├── profile/                          com.android.library
│   └── chatdetail/                       com.android.library
├── build-logic/                          convention plugins
├── gradle/
│   └── libs.versions.toml
└── docs/superpowers/specs/
```

### 3.1 Правила зависимостей (compiler-enforced)

| Модуль | Может зависеть от |
|--------|-------------------|
| `:app` | все `:feature:*`, все `:core:*`, `:components` |
| `:feature:*` | все `:core:*`, `:components` |
| `:core:ui` | `:core:model`, `:components` |
| `:core:data` | `:core:model` |
| `:core:navigation` | `:core:model` (только публичные модели для nav-аргументов) |
| `:core:model` | — (чистые data-классы, без android-зависимостей) |

**Между `:feature:*` зависимостей нет.** Если фиче нужен переход в другую фичу — через route в `:core:navigation`.

### 3.2 Composite build

В `settings.gradle.kts` корня android-template:

```kotlin
rootProject.name = "AndroidTemplate"

pluginManagement { /* как в android-components */ }
dependencyResolutionManagement { /* как в android-components */ }

includeBuild("../android-components") {
    dependencySubstitution {
        substitute(module("com.example:components")).using(project(":components"))
    }
}

include(
    ":app",
    ":core:model", ":core:data", ":core:ui", ":core:navigation",
    ":feature:chats", ":feature:spaces", ":feature:calls", ":feature:profile", ":feature:chatdetail",
)
```

Каждый feature- и core-модуль (кроме `:core:model`) объявляет `implementation("com.example:components")` — composite build резолвит в соседний модуль `:components` репозитория `android-components`.

### 3.3 Изменение на стороне android-components

В `android-components/components/.../DSBrand.kt` добавляется один factory-метод в `companion object`:

```kotlin
fun byCodename(codename: String): DSBrand
```

Он принимает один из NATO-кодовых имён (`foxtrot`/`tango`/`sierra`/`kilo`/`love`) и возвращает соответствующий enum-инстанс `DSBrand`. На неизвестное имя — `error(...)`. Это единственная правка в android-components, необходимая для android-template. Внутренние enum-константы остаются скрытыми за публичным API factory.

---

## 4. Навигация

### 4.1 Структура

Двухуровневая Compose Navigation:

```
Root NavHost (app)
├── "main"                  ← Scaffold с BottomTabs + nested NavHost
│   └── Nested NavHost
│       ├── "tab/chats"     ← feature:chats
│       ├── "tab/spaces"    ← feature:spaces
│       ├── "tab/calls"     ← feature:calls
│       └── "tab/profile"   ← feature:profile
└── "chat/{chatId}"         ← feature:chatdetail (full-screen, без bottom bar)
```

**Обоснование:**
- Каждая вкладка имеет свой back-stack (state сохраняется при переключении табов)
- Chat detail доступен из «Чаты» и «Пространства», на нём bottom bar скрыт — поэтому он выше уровня bottom-bar Scaffold
- Back из chat detail возвращает на вкладку, откуда зашли

### 4.2 Контракты в `:core:navigation`

```kotlin
sealed class NavRoute(val route: String) {
    data object Main : NavRoute("main")
    data class ChatDetail(val chatId: String) : NavRoute("chat/$chatId") {
        companion object { const val PATTERN = "chat/{chatId}"; const val ARG_CHAT_ID = "chatId" }
    }
}

sealed class TabRoute(val route: String) {
    data object Chats    : TabRoute("tab/chats")
    data object Spaces   : TabRoute("tab/spaces")
    data object Calls    : TabRoute("tab/calls")
    data object Profile  : TabRoute("tab/profile")

    companion object { val all = listOf(Chats, Spaces, Calls, Profile) }
}

fun NavController.navigateToChatDetail(chatId: String) {
    navigate(NavRoute.ChatDetail(chatId).route)
}

fun NavController.navigateToTab(tab: TabRoute) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
```

### 4.3 Регистрация destinations

Каждый `:feature:*` экспортирует extension-функцию:

```kotlin
// feature:chats
fun NavGraphBuilder.chatsScreen(onChatClick: (String) -> Unit) {
    composable(TabRoute.Chats.route) { ChatsScreen(onChatClick = onChatClick) }
}
```

`:app` склеивает:

```kotlin
NavHost(navController, startDestination = NavRoute.Main.route) {
    composable("main") { MainScaffold(rootNavController = navController) }
    composable(NavRoute.ChatDetail.PATTERN) { entry ->
        val chatId = entry.arguments?.getString(NavRoute.ChatDetail.ARG_CHAT_ID)!!
        ChatDetailScreen(chatId = chatId, onBack = { navController.popBackStack() })
    }
}
```

`MainScaffold`:

```kotlin
val tabNav = rememberNavController()
Scaffold(bottomBar = { BottomTabsHost(tabNav) }) { padding ->
    NavHost(tabNav, startDestination = TabRoute.Chats.route, modifier = Modifier.padding(padding)) {
        chatsScreen(onChatClick = { rootNavController.navigateToChatDetail(it) })
        spacesScreen(onChatClick = { rootNavController.navigateToChatDetail(it) })
        callsScreen()
        profileScreen()
    }
}
```

---

## 5. Модель данных

### 5.1 Доменные модели (в `:core:model`)

Все модели — `@Serializable` data-классы, никаких android-зависимостей.

```kotlin
data class CurrentUser(
    val id: String, val name: String, val avatar: AvatarSpec,
    val statusMessage: String?, val email: String?, val phone: String?,
)

data class User(val id: String, val name: String, val avatar: AvatarSpec, val status: UserStatus)

sealed class UserStatus {
    data object Online : UserStatus()
    data object Offline : UserStatus()
    data class LastSeen(val timestamp: Long) : UserStatus()
}

data class Space(val id: String, val name: String, val avatar: AvatarSpec, val accentIndex: Int)

data class AvatarSpec(
    val type: AvatarType,           // соответствует 5 AvatarView types
    val initials: String?,
    val gradientIndex: Int,
)
enum class AvatarType { Person, Group, Channel, Workspace, Self }

data class Chat(
    val id: String,
    val type: ChatType,             // P2P | Group | Channel
    val title: String,
    val avatar: AvatarSpec,
    val spaceId: String?,           // null для P2P, обязательно для Group/Channel
    val participantIds: List<String>,
    val lastMessage: MessagePreview,
    val unreadCount: Int = 0,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val mention: Boolean = false,
    val hasReaction: Boolean = false,
)
enum class ChatType { P2P, Group, Channel }

data class MessagePreview(
    val text: String,
    val kind: PreviewKind,          // соответствует ChatListItemView preview types
    val timestamp: Long,
    val ownStatus: MessageStatus = MessageStatus.NONE,
)
enum class PreviewKind { Text, Group, Draft, Typing }
enum class MessageStatus { NONE, SENDING, DELIVERED, READ, ERROR }

@Serializable
sealed class Message {
    abstract val id: String
    abstract val chatId: String
    abstract val senderId: String
    abstract val timestamp: Long
    abstract val isMine: Boolean
    abstract val status: MessageStatus
    abstract val reactions: List<Reaction>
    abstract val replyToId: String?

    @Serializable @SerialName("text")
    data class Text(/* common fields */, val body: String, val formatting: List<TextSpan> = emptyList()) : Message()

    @Serializable @SerialName("media")
    data class Media(/* common */, val attachments: List<MediaAttachment>, val caption: String?) : Message()

    @Serializable @SerialName("voice")
    data class Voice(/* common */, val durationMs: Long, val waveform: List<Int>, val transcription: String?) : Message()

    @Serializable @SerialName("link")
    data class Link(/* common */, val url: String, val title: String, val description: String?, val imageUrl: String?, val body: String?) : Message()

    @Serializable @SerialName("callmeet")
    data class CallMeet(/* common */, val callStatus: CallStatus, val isVideo: Boolean, val isGroupCall: Boolean, val durationMs: Long?) : Message()
}

data class Reaction(val emoji: String, val count: Int, val isMine: Boolean)

data class MediaAttachment(val placeholderColor: String, val type: AttachmentType, val durationMs: Long?)
enum class AttachmentType { Photo, Video, File }

enum class CallStatus { Answered, Rejected, Missed, NoAnswer }

data class TextSpan(val start: Int, val endExclusive: Int, val style: SpanStyle)
enum class SpanStyle { Bold, Italic, Underline, Strikethrough, Mono, Link }

data class Call(
    val id: String,
    val type: CallType,             // Incoming | Outgoing | Missed
    val counterpartId: String,
    val counterpartName: String,
    val counterpartAvatar: AvatarSpec,
    val isVideo: Boolean,
    val isGroupCall: Boolean,
    val timestamp: Long,
    val durationMs: Long?,
)
enum class CallType { Incoming, Outgoing, Missed }
```

Дискриминатор полиморфных сообщений в JSON — `"type"` (через `Json { classDiscriminator = "type" }`).

### 5.2 JSON-файлы

```
app/src/main/assets/mock/
├── current-user.json       объект CurrentUser
├── users.json              массив User
├── spaces.json             массив Space
├── chats.json              массив Chat (все типы)
├── calls.json              массив Call
└── messages/
    ├── <chatId>.json       массив Message по чату
    └── ...
```

Пример `messages/chat-001.json`:

```json
[
  {"type":"text","id":"m-1","chatId":"chat-001","senderId":"u-2","timestamp":1715600000000,
   "isMine":false,"status":"NONE","reactions":[],"replyToId":null,"body":"Привет","formatting":[]},
  {"type":"voice","id":"m-2","chatId":"chat-001","senderId":"u-1","timestamp":1715600060000,
   "isMine":true,"status":"READ","reactions":[],"replyToId":null,
   "durationMs":4200,"waveform":[2,4,6,8,6,4,2],"transcription":null}
]
```

### 5.3 Стратегия загрузки

| Файл | Когда читается |
|------|----------------|
| `current-user.json`, `users.json`, `spaces.json`, `chats.json`, `calls.json` | один раз при старте app, в фоне (init `MockRepositoryImpl`) |
| `messages/<chatId>.json` | лениво при открытии конкретного чата |

### 5.4 Repository API (в `:core:data`)

```kotlin
interface MessengerRepository {
    val currentUser: StateFlow<CurrentUser>
    val users: StateFlow<List<User>>
    val spaces: StateFlow<List<Space>>
    val currentSpaceId: StateFlow<String>
    fun setCurrentSpace(id: String)

    val p2pChats: StateFlow<List<Chat>>                         // фильтр: type == P2P
    fun spaceChats(spaceId: String): StateFlow<List<Chat>>      // фильтр: spaceId == id

    val calls: StateFlow<List<Call>>

    suspend fun loadMessages(chatId: String): StateFlow<List<Message>>
    suspend fun sendTextMessage(chatId: String, body: String)

    fun getUser(id: String): User?
    fun getChat(id: String): Chat?
}
```

**Реализация `sendTextMessage`:**
1. Создаёт `Message.Text(isMine=true, status=SENDING)`, добавляет в in-memory кэш для `chatId`
2. После ~400 мс меняет статус на `DELIVERED`
3. После ещё ~800 мс — на `READ`
4. Обновляет `Chat.lastMessage` соответствующего чата → строка в списке отражает изменение
5. Перезапуск app — всё чисто (JSON не модифицируется)

---

## 6. Build-time бренд

Бренд задаётся на этапе сборки. UI не имеет switcher'а. Системная тема (Light/Dark) определяется по `isSystemInDarkTheme()`.

### 6.1 Gradle property → BuildConfig

```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        val brand = (project.findProperty("brand") as String? ?: "foxtrot").lowercase()
        val allowed = setOf("foxtrot", "tango", "sierra", "kilo", "love")
        require(brand in allowed) { "Unknown brand: $brand. Allowed: $allowed" }
        buildConfigField("String", "BRAND_CODENAME", "\"$brand\"")
    }
    buildFeatures { buildConfig = true }
}
```

### 6.2 Применение в `:core:ui`

```kotlin
val AppBrand: DSBrand = DSBrand.byCodename(BuildConfig.BRAND_CODENAME)
val AppBrandCodename: String = BuildConfig.BRAND_CODENAME

val LocalAppBrand = staticCompositionLocalOf<DSBrand> { error("AppBrand not provided") }
val LocalIsDark = staticCompositionLocalOf<Boolean> { error("IsDark not provided") }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalAppBrand provides AppBrand,
        LocalIsDark provides isDark,
    ) { content() }
}
```

Фичи получают brand через `LocalAppBrand.current` и передают в `<X>ColorScheme.from(brand, isDark)`.

### 6.3 Команды сборки

```
./gradlew :app:assembleDebug                       → foxtrot (default)
./gradlew :app:assembleDebug -Pbrand=tango         → tango
./gradlew :app:assembleDebug -Pbrand=sierra        → sierra
./gradlew :app:assembleDebug -Pbrand=kilo          → kilo
./gradlew :app:assembleDebug -Pbrand=love          → love
```

### 6.4 Что меняется при смене бренда

Меняется: акцентные цвета компонентов, градиенты фонов (BackgroundPatternView), gradient-аватары, брендированные иконки (`DSIcon.coloredNamed`).

Не меняется: монохромные иконки (`DSIcon.named`), типографика, радиусы, отступы.

### 6.5 Constraint на исходники

В исходниках android-template **запрещены внутренние enum-имена `DSBrand`** (имена, которые видны в файле `DSBrand.kt` android-components). Конвертация codename → enum происходит внутри `DSBrand.byCodename(...)` в android-components, наружу enum-имена не утекают.

---

## 7. Карта экранов

### 7.1 Сводная таблица

| Экран | Header (HeadersView) | Контент | Компоненты из `:components` |
|-------|---------------------|---------|------------------------------|
| **Чаты** (root) | `Main(title="Чаты", mode=Mode.CHATS, search=On)` | LazyColumn строк чатов | ChatListItemView |
| **Пространства** (root) | `Main(title=<currentSpaceName>, mode=Mode.SPACES, search=On)` + bottom-sheet с пространствами | LazyColumn строк чатов выбранного space | ChatListItemView, AvatarView (в sheet) |
| **Звонки** (root) | `Main(title="Звонки", mode=Mode.CALLS, search соответствует моде)` | LazyColumn `CallRow` | AvatarView, DSIcon |
| **Профиль** (root) | `Main(title="Профиль", mode=Mode.PROFILE)` | Large avatar + имя + статус + строки | AvatarView (Self), `ProfileRow` |
| **Chat detail** | `P2P` / `Group` / `Channel` (по `Chat.type`) | BackgroundPatternView + LazyColumn бабблов + MessagePanelView | BackgroundPatternView, BubblesView, MediaBubbleView, VoiceBubbleView, LinkBubbleView, CallMeetView, ReactionsView, MessagePanelView |
| **Bottom bar** | — | — | BottomTabsView (иконки уже встроены) |

Точные значения параметров `mode` и `search` для `HeaderConfig.Main` уточняются на имплементации по сигнатуре в `HeadersView.kt`.

### 7.2 Детали по экранам

**Чаты (P2P)** — `:feature:chats`
- ViewModel: `chats: StateFlow<List<Chat>>` ← `repository.p2pChats`
- Тап по строке → `rootNavController.navigateToChatDetail(chat.id)`
- Empty state: текстовый плейсхолдер «Нет личных чатов»
- Search bar в хедере отрисовывается (поведение оверлея вне scope)
- Кнопка «+» в хедере — пока no-op

**Пространства** — `:feature:spaces`
- ViewModel: `currentSpace`, `chats` ← `repository.currentSpaceId.flatMapLatest { repository.spaceChats(it) }`
- Switcher: тап на хедер → `SpaceSwitcherSheet` (Material3 `ModalBottomSheet`, список с AvatarView+имя, тап → `repository.setCurrentSpace(id)`, sheet закрывается)
- Тот же тап-в-чат → `navigateToChatDetail`
- Empty state если в пространстве нет чатов: «В этом пространстве нет чатов»

**Звонки** — `:feature:calls`
- ViewModel: `calls: StateFlow<List<Call>>` ← `repository.calls`
- Готовой View-компоненты строки звонка в `:components` нет → `CallRow` собирается в `:core:ui` из примитивов: `AvatarView` + иконка типа звонка (incoming/outgoing/missed/video из icons-library) + имя + длительность + относительное время
- Тап по строке — пока no-op (вне scope)
- Empty state: «История звонков пуста»

**Профиль** — `:feature:profile`
- ViewModel: `user: StateFlow<CurrentUser>` ← `repository.currentUser`
- Large `AvatarView(Self)` сверху, под ним имя (`DSTypography.heading`), статус (`DSTypography.body`)
- `ProfileRow`-ы (в `:core:ui`): email, phone, версия приложения (`BuildConfig.VERSION_NAME`), `AppBrandCodename` (для диагностики)
- Без edit-режима

**Chat detail** — `:feature:chatdetail`
- Принимает `chatId` через nav-аргумент
- ViewModel: `chat`, `messages: StateFlow<List<Message>>` ← `repository.getChat(chatId)`, `repository.loadMessages(chatId)`
- Header выбирается по `Chat.type`:
  - `P2P` → `HeaderConfig.P2P(avatar, name, online/lastSeen, callButton, videoCallButton)`
  - `Group` → `HeaderConfig.Group(avatar, name, memberCount, callButton)`
  - `Channel` → `HeaderConfig.Channel(avatar, name, subscriberCount, callButton)`
- `BackgroundPatternView` — фон, palette index фиксирован (T1)
- `LazyColumn` бабблов: тип сообщения → соответствующий View-компонент. Группировка сообщений по дням опционально (на имплементации решим, если нужно).
- `ReactionsView` под бабблами — статичные из JSON, тап по реакции no-op
- `MessagePanelView` — нижняя панель. **Wired только text send:** ввод → нажатие send → `viewModel.sendText(body)` → `repository.sendTextMessage(chatId, body)`
- Voice playback: VoiceBubbleView переключает PAUSED/PLAYING с fake-прогрессом без реального audio. AudioPanelView — вне scope.

### 7.3 Custom composables в `:core:ui`

- `AppTheme` — обёртка с `LocalAppBrand`, `LocalIsDark`
- `AppScaffold` — корневая структура root-вкладок (фон, отступы system bars)
- `HeaderHost` — Compose-обёртка над `HeadersView` (AndroidView + configure)
- `ChatListHost` — обёртка над списком `ChatListItemView`
- `MessageList` — LazyColumn-обёртка с роутингом по типу сообщения на нужный bubble component
- `MessagePanelHost` — обёртка над `MessagePanelView`
- `BottomTabsHost` — обёртка над `BottomTabsView`
- `CallRow` — строка звонка
- `ProfileRow` — строка профиля
- `SpaceSwitcherSheet` — bottom sheet выбора пространства
- `EmptyState` — общий компонент empty state

Все `*Host`-обёртки — тонкие адаптеры: `AndroidView { factory } + LaunchedEffect(config) { view.configure(config) }`, без своей логики.

### 7.4 Иконки и брендирование

- Иконки bottom tabs — встроены в `BottomTabsView`, дополнительной конфигурации не требуют
- Иконки в кнопках хедера, ProfileRow, CallRow, MessagePanel — через `DSIcon.named(...)` / `DSIcon.coloredNamed(...)` из icons-library
- Цвета любых элементов — только через `<X>ColorScheme.from(brand, isDark)` методы каждого компонента

### 7.5 Figma-референс

- Вкладка «Чаты»: `https://www.figma.com/design/VVccyf630TP7LcIFOKeVYk/Comp.Mobile.Frisbee-2.0?node-id=5765-2345`
- Остальные экраны: предоставляются по требованию при имплементации
- Имя Figma-файла — внешняя данность дизайн-команды; в исходники android-template не попадает

---

## 8. State и DI

### 8.1 ViewModels

Используются классы `androidx.lifecycle.ViewModel` с `mutableStateOf` / `StateFlow` для observable-состояния. Каждый screen имеет свой ViewModel.

### 8.2 DI

Без Hilt/Koin/Dagger. Ручной контейнер `AppContainer` создаётся в `Application.onCreate()`, прокидывается через `LocalAppContainer` CompositionLocal. ViewModel-фабрики получают зависимости из `AppContainer`.

```kotlin
class AppContainer(context: Context) {
    val repository: MessengerRepository = MockRepositoryImpl(context.applicationContext)
}
```

Достаточно для прототипа. При росте сложности — заменяется на Hilt без переписывания фич.

---

## 9. Тестирование

Прототип, тестирование минимальное:

- `:core:data` — unit-тесты на парсинг JSON и фильтрацию чатов (`p2pChats`, `spaceChats`, transitions статусов при `sendTextMessage`)
- `:core:model` — без тестов (чистые data-классы)
- `:feature:*` — без unit-тестов (visual prototype)
- Smoke-тест: `:app` собирается с каждым из 5 брендов, приложение запускается и отображает все 4 root-экрана

CI/CD — вне scope этого spec.

---

## 10. Сборка и запуск

**Требования (как в android-components):**
- Android SDK (compileSdk 34, minSdk 26)
- JDK 17 (Android Studio JBR)
- Локальные клоны: `../android-components`, `../icons-library` (требование android-components)

**Команды:**
```
JAVA_HOME="/path/to/Android Studio/jbr" ./gradlew :app:assembleDebug
JAVA_HOME="/path/to/Android Studio/jbr" ./gradlew :app:installDebug
JAVA_HOME="/path/to/Android Studio/jbr" ./gradlew :app:installDebug -Pbrand=tango
```

---

## 11. Клонирование под гипотезу

Будущий процесс: каркас клонируется → один или несколько `:feature:*` модулей модифицируются → проверяется гипотеза. Благодаря модульным границам:
- Можно временно отключить вкладку, убрав её регистрацию в `:app/MainScaffold` (модуль остаётся в проекте)
- Можно переписать один feature без затрагивания других
- Можно подменить `MockRepositoryImpl` экспериментальной реализацией для отдельной гипотезы

---

## 12. Открытые вопросы (на имплементацию)

- Точная сигнатура `HeaderConfig.Main`: какие именно поля называются `mode`, `search`, как ведут себя в Profile/Calls режимах — уточнить чтением `HeadersView.kt`
- Имена иконок в icons-library для CallRow (incoming/outgoing/missed/video) — выбрать на имплементации
- Параметры `mode` для Profile-экрана — есть ли такая `Mode` в текущем enum или использовать custom header
- Группировка сообщений по дням в chat detail — на имплементации (если визуально требуется по макетам)
