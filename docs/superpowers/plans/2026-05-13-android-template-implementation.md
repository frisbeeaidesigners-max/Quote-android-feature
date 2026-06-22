# android-template — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Собрать каркас Android-мессенджера с 4 root-вкладками (Чаты P2P / Пространства / Звонки / Профиль) + chat detail, подключив `android-components` через Gradle composite build и заложив build-time бренд из 5 NATO-кодовых имён.

**Architecture:** Многомодульный проект (`:app` + `core/*` + `feature/*`) в Now-in-Android стиле. Бренд выбирается через `-Pbrand=<codename>` → `BuildConfig.BRAND_CODENAME` → `DSBrand.byCodename(...)` → `LocalAppBrand`. Mock-данные в JSON под `app/src/main/assets/mock/`, парсятся `kotlinx-serialization` в `:core:data`. Compose Navigation: root NavHost с `"main"` (Scaffold + nested NavHost для табов) и `"chat/{chatId}"` (full-screen).

**Tech Stack:** Kotlin 1.9.22, AGP 8.2.2, Compose BOM 2024.02.00, Compose Compiler 1.5.10, kotlinx-serialization-json, androidx.navigation:navigation-compose, Material 3. compileSdk 34, minSdk 26, JDK 17 (Android Studio JBR).

**Spec:** `docs/superpowers/specs/2026-05-13-android-template-design.md` — этот план реализует все секции spec.

---

## File Structure Overview

```
android-template/
├── settings.gradle.kts                          # composite build с android-components
├── build.gradle.kts                              # root plugins
├── gradle.properties
├── gradle/libs.versions.toml                     # Version Catalog
├── build-logic/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       ├── android.library.convention.gradle.kts
│       ├── android.feature.convention.gradle.kts
│       └── android.application.convention.gradle.kts
├── app/
│   ├── build.gradle.kts                          # com.android.application + brand BuildConfig
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/template/
│       │   ├── TemplateApp.kt                    # Application class + AppContainer
│       │   ├── MainActivity.kt                   # NavHost root
│       │   ├── MainScaffold.kt                   # Bottom tabs scaffold + nested NavHost
│       │   └── AppContainer.kt                   # ручной DI
│       ├── assets/mock/
│       │   ├── current-user.json
│       │   ├── users.json
│       │   ├── spaces.json
│       │   ├── chats.json
│       │   ├── calls.json
│       │   └── messages/
│       │       ├── chat-001.json … chat-NNN.json
│       └── res/
│           ├── values/strings.xml
│           └── values/themes.xml
├── core/
│   ├── model/                                    # data classes, без android-deps
│   │   └── src/main/java/com/example/template/core/model/
│   │       ├── User.kt
│   │       ├── Space.kt
│   │       ├── Chat.kt
│   │       ├── Message.kt
│   │       ├── Call.kt
│   │       ├── Avatar.kt
│   │       └── Status.kt
│   ├── data/
│   │   └── src/
│   │       ├── main/java/com/example/template/core/data/
│   │       │   ├── MessengerRepository.kt        # интерфейс
│   │       │   ├── MockRepositoryImpl.kt         # реализация
│   │       │   ├── JsonModule.kt                 # Json instance + serializer config
│   │       │   └── dto/                          # @Serializable DTO-классы зеркалят :core:model
│   │       └── test/java/com/example/template/core/data/
│   │           ├── JsonParsingTest.kt
│   │           ├── RepositoryFilterTest.kt
│   │           └── SendMessageTest.kt
│   ├── navigation/
│   │   └── src/main/java/com/example/template/core/navigation/
│   │       ├── NavRoute.kt
│   │       ├── TabRoute.kt
│   │       └── NavExtensions.kt
│   └── ui/
│       └── src/main/java/com/example/template/core/ui/
│           ├── AppBrand.kt                       # читает BuildConfig
│           ├── AppTheme.kt                       # LocalAppBrand / LocalIsDark
│           ├── AppScaffold.kt
│           ├── hosts/
│           │   ├── HeaderHost.kt
│           │   ├── BottomTabsHost.kt
│           │   ├── ChatListHost.kt
│           │   ├── MessageList.kt
│           │   └── MessagePanelHost.kt
│           ├── rows/
│           │   ├── CallRow.kt
│           │   └── ProfileRow.kt
│           └── components/
│               ├── SpaceSwitcherSheet.kt
│               └── EmptyState.kt
├── feature/
│   ├── chats/
│   │   └── src/main/java/com/example/template/feature/chats/
│   │       ├── ChatsViewModel.kt
│   │       ├── ChatsScreen.kt
│   │       └── ChatsNavGraph.kt                  # NavGraphBuilder.chatsScreen(...)
│   ├── spaces/.../{SpacesViewModel,SpacesScreen,SpacesNavGraph}.kt
│   ├── calls/.../{CallsViewModel,CallsScreen,CallsNavGraph}.kt
│   ├── profile/.../{ProfileViewModel,ProfileScreen,ProfileNavGraph}.kt
│   └── chatdetail/.../{ChatDetailViewModel,ChatDetailScreen,ChatDetailNavGraph}.kt
└── docs/superpowers/
    ├── specs/2026-05-13-android-template-design.md
    └── plans/2026-05-13-android-template-implementation.md     ← этот файл
```

---

## Section A — Foundation (gradle skeleton + composite build)

Цель: пустой многомодульный проект собирается, `:app` подключает `:components` из соседнего репо через composite build, `./gradlew :app:assembleDebug` проходит.

### Task A1: Root gradle files + Version Catalog

**Files:**
- Create: `android-template/settings.gradle.kts`
- Create: `android-template/build.gradle.kts`
- Create: `android-template/gradle.properties`
- Create: `android-template/gradle/libs.versions.toml`
- Create: `android-template/.gitignore`

- [ ] **Step 1: Создать `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.2.2"
kotlin = "1.9.22"
compose-bom = "2024.02.00"
compose-compiler = "1.5.10"
compose-navigation = "2.7.7"
lifecycle = "2.7.0"
kotlinx-serialization = "1.6.3"
kotlinx-coroutines = "1.7.3"
core-ktx = "1.12.0"
activity-compose = "1.8.2"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "core-ktx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "compose-navigation" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinx-coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
components = { group = "com.example", name = "components", version = "unspecified" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Создать `settings.gradle.kts`**

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AndroidTemplate"

includeBuild("../android-components") {
    dependencySubstitution {
        substitute(module("com.example:components")).using(project(":components"))
    }
}

include(
    ":app",
    ":core:model",
    ":core:data",
    ":core:ui",
    ":core:navigation",
    ":feature:chats",
    ":feature:spaces",
    ":feature:calls",
    ":feature:profile",
    ":feature:chatdetail",
)
```

- [ ] **Step 3: Создать root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

- [ ] **Step 4: Создать `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 5: Создать `.gitignore`**

```
.gradle/
build/
local.properties
*.iml
.idea/
captures/
.cxx/
```

- [ ] **Step 6: Скопировать gradle wrapper**

Скопировать `gradle/wrapper/` (gradle-wrapper.jar и gradle-wrapper.properties), `gradlew`, `gradlew.bat` из `android-components/`. Убедиться, что версия gradle совпадает.

- [ ] **Step 7: Commit**

```bash
cd C:/Claude/test_project/android-template
git init
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/ gradlew gradlew.bat .gitignore
git commit -m "chore: bootstrap gradle project with version catalog"
```

---

### Task A2: Convention plugins (build-logic)

**Files:**
- Create: `build-logic/settings.gradle.kts`
- Create: `build-logic/build.gradle.kts`
- Create: `build-logic/src/main/kotlin/android.library.convention.gradle.kts`
- Create: `build-logic/src/main/kotlin/android.feature.convention.gradle.kts`
- Create: `build-logic/src/main/kotlin/android.application.convention.gradle.kts`
- Create: `build-logic/src/main/kotlin/kotlin.library.convention.gradle.kts`

- [ ] **Step 1: `build-logic/settings.gradle.kts`**

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
```

- [ ] **Step 2: `build-logic/build.gradle.kts`**

```kotlin
plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.findLibrary("compose-bom").get())
    compileOnly("com.android.tools.build:gradle:${libs.findVersion("agp").get()}")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.findVersion("kotlin").get()}")
}
```

- [ ] **Step 3: `android.library.convention.gradle.kts`**

```kotlin
import org.gradle.api.JavaVersion

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}
```

- [ ] **Step 4: `android.feature.convention.gradle.kts`**

```kotlin
plugins {
    id("android.library.convention")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

android {
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    add("implementation", composeBom)
    add("implementation", libs.compose.ui)
    add("implementation", libs.compose.foundation)
    add("implementation", libs.compose.material3)
    add("implementation", libs.compose.ui.tooling.preview)
    add("debugImplementation", libs.compose.ui.tooling)
    add("implementation", libs.androidx.lifecycle.viewmodel.compose)
    add("implementation", libs.androidx.navigation.compose)
    add("implementation", libs.kotlinx.coroutines.android)
    add("implementation", libs.components)
}
```

- [ ] **Step 5: `android.application.convention.gradle.kts`**

```kotlin
import org.gradle.api.JavaVersion

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        targetSdk = 34
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions { jvmTarget = "17" }
}
```

- [ ] **Step 6: `kotlin.library.convention.gradle.kts`**

```kotlin
import org.gradle.api.JavaVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions { jvmTarget = "17" }
}
```

- [ ] **Step 7: Commit**

```bash
git add build-logic/
git commit -m "build: add convention plugins for library/feature/application"
```

---

### Task A3: Создать все 10 модулей (пустые build.gradle.kts)

**Files:**
- Create: `app/build.gradle.kts` (+ `src/main/AndroidManifest.xml`)
- Create: `core/model/build.gradle.kts`
- Create: `core/data/build.gradle.kts` (+ `src/main/AndroidManifest.xml`)
- Create: `core/ui/build.gradle.kts` (+ `src/main/AndroidManifest.xml`)
- Create: `core/navigation/build.gradle.kts` (+ `src/main/AndroidManifest.xml`)
- Create: `feature/{chats,spaces,calls,profile,chatdetail}/build.gradle.kts` (+ manifests)

- [ ] **Step 1: `core/model/build.gradle.kts`**

```kotlin
plugins {
    id("kotlin.library.convention")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
```

- [ ] **Step 2: `core/data/build.gradle.kts`**

```kotlin
plugins {
    id("android.library.convention")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.template.core.data"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.android)
}
```

- [ ] **Step 3: `core/data/src/main/AndroidManifest.xml`**

```xml
<manifest />
```

- [ ] **Step 4: `core/navigation/build.gradle.kts`**

```kotlin
plugins {
    id("android.library.convention")
}

android {
    namespace = "com.example.template.core.navigation"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.navigation.compose)
}
```

- [ ] **Step 5: `core/navigation/src/main/AndroidManifest.xml`**

```xml
<manifest />
```

- [ ] **Step 6: `core/ui/build.gradle.kts`**

```kotlin
plugins {
    id("android.feature.convention")
}

android {
    namespace = "com.example.template.core.ui"
    buildFeatures { buildConfig = false }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.components)
}
```

- [ ] **Step 7: `core/ui/src/main/AndroidManifest.xml`**

```xml
<manifest />
```

- [ ] **Step 8: Для каждого из `feature/{chats,spaces,calls,profile,chatdetail}/build.gradle.kts`**

```kotlin
plugins {
    id("android.feature.convention")
}

android {
    namespace = "com.example.template.feature.<NAME>"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
}
```

Где `<NAME>` = `chats`, `spaces`, `calls`, `profile`, `chatdetail` для соответствующего модуля.

- [ ] **Step 9: Для каждого `feature/*/src/main/AndroidManifest.xml`**

```xml
<manifest />
```

- [ ] **Step 10: `app/build.gradle.kts`**

```kotlin
plugins {
    id("android.application.convention")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.template"
    defaultConfig {
        applicationId = "com.example.template"
        versionCode = 1
        versionName = "0.1.0"

        val brand = (project.findProperty("brand") as String? ?: "foxtrot").lowercase()
        val allowed = setOf("foxtrot", "tango", "sierra", "kilo", "love")
        require(brand in allowed) { "Unknown brand: $brand. Allowed: $allowed" }
        buildConfigField("String", "BRAND_CODENAME", "\"$brand\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:chats"))
    implementation(project(":feature:spaces"))
    implementation(project(":feature:calls"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:chatdetail"))
    implementation(libs.components)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.serialization.json)
}
```

- [ ] **Step 11: `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".TemplateApp"
        android:label="Template"
        android:theme="@style/Theme.Template"
        android:allowBackup="false">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustNothing">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 12: `app/src/main/res/values/themes.xml`**

```xml
<resources>
    <style name="Theme.Template" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 13: `app/src/main/res/values/strings.xml`**

```xml
<resources>
    <string name="app_name">Template</string>
</resources>
```

- [ ] **Step 14: Заглушки Kotlin-файлов, чтобы модули компилировались пустыми**

В каждом из `core/{model,data,navigation,ui}` и `feature/*` создать пустой пакет-файл `package-info` через файл:

`core/model/src/main/java/com/example/template/core/model/Placeholder.kt`:
```kotlin
package com.example.template.core.model
internal object Placeholder
```

Аналогично для остальных модулей (имена пакетов соответствующие).

`app/src/main/java/com/example/template/TemplateApp.kt`:
```kotlin
package com.example.template

import android.app.Application

class TemplateApp : Application()
```

`app/src/main/java/com/example/template/MainActivity.kt`:
```kotlin
package com.example.template

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Template scaffold OK") }
    }
}
```

- [ ] **Step 15: Verify build**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (Если ошибки composite build — см. Task A4.)

- [ ] **Step 16: Commit**

```bash
git add app/ core/ feature/
git commit -m "feat: scaffold all 10 modules with empty build files"
```

---

### Task A4: Verify composite build с android-components

**Files:** —

- [ ] **Step 1: Запустить smoke-build приложения**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug -PRand=foxtrot --info | head -100`

Expected: `:components` из `../android-components` участвует в build graph (видно в логе `Included build 'android-components'`).

- [ ] **Step 2: Если build падает на substitution**

Проверить, что в `android-components/components/build.gradle.kts` модуль публикуется с координатами `com.example:components`. Если нет — добавить в android-components:

```kotlin
// android-components/components/build.gradle.kts
group = "com.example"
version = "unspecified"
```

Это minimal-правка, чтобы dependencySubstitution смог сматчить.

- [ ] **Step 3: Verify**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit (если правились файлы в android-components)**

```bash
cd ../android-components
git add components/build.gradle.kts
git commit -m "build: expose components module as com.example:components for composite consumers"
cd ../android-template
```

---

## Section B — DSBrand factory + brand pipeline

### Task B1: Добавить `DSBrand.byCodename` в android-components

**Files:**
- Modify: `../android-components/components/src/main/java/com/example/components/designsystem/DSBrand.kt`

- [ ] **Step 1: Найти конец enum DSBrand**

Read: `../android-components/components/src/main/java/com/example/components/designsystem/DSBrand.kt`
Найти строку `companion object {` если есть, либо место перед `}` закрывающим enum.

- [ ] **Step 2: Добавить companion object с factory**

Если companion object уже есть — добавить метод в него. Если нет — добавить блок. Метод — `fun byCodename(codename: String): DSBrand` с `when (codename.lowercase()) { ... }`, где каждый NATO-codename (`"foxtrot"`, `"tango"`, `"sierra"`, `"kilo"`, `"love"`) маппится в соответствующий enum-инстанс `DSBrand` из того же файла. Неизвестный codename → `error("Unknown brand codename: $codename")`.

Разместить **внутри** `enum class DSBrand(...)` перед закрывающей скобкой.

- [ ] **Step 3: Verify build**

Run (из корня android-components): `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :components:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
cd ../android-components
git add components/src/main/java/com/example/components/designsystem/DSBrand.kt
git commit -m "feat(brand): add DSBrand.byCodename factory for codename → enum"
cd ../android-template
```

---

### Task B2: `:core:ui` AppBrand + AppTheme

**Files:**
- Modify: `core/ui/build.gradle.kts` (включить BuildConfig)
- Create: `core/ui/src/main/java/com/example/template/core/ui/AppBrand.kt`
- Create: `core/ui/src/main/java/com/example/template/core/ui/AppTheme.kt`

- [ ] **Step 1: Включить buildConfig в `:core:ui`**

Edit `core/ui/build.gradle.kts`:

```kotlin
plugins {
    id("android.feature.convention")
}

android {
    namespace = "com.example.template.core.ui"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        val brand = (project.findProperty("brand") as String? ?: "foxtrot").lowercase()
        val allowed = setOf("foxtrot", "tango", "sierra", "kilo", "love")
        require(brand in allowed) { "Unknown brand: $brand. Allowed: $allowed" }
        buildConfigField("String", "BRAND_CODENAME", "\"$brand\"")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.components)
}
```

Это позволяет `:core:ui` читать brand из своего BuildConfig (а не зависеть от BuildConfig в `:app`, что нарушило бы дизайн).

- [ ] **Step 2: `AppBrand.kt`**

```kotlin
package com.example.template.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.components.designsystem.DSBrand

val AppBrand: DSBrand = DSBrand.byCodename(BuildConfig.BRAND_CODENAME)
val AppBrandCodename: String = BuildConfig.BRAND_CODENAME

val LocalAppBrand = staticCompositionLocalOf<DSBrand> { error("AppBrand not provided") }
val LocalIsDark = staticCompositionLocalOf<Boolean> { error("IsDark not provided") }
```

- [ ] **Step 3: `AppTheme.kt`**

```kotlin
package com.example.template.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalAppBrand provides AppBrand,
        LocalIsDark provides isDark,
    ) {
        content()
    }
}
```

- [ ] **Step 4: Verify build**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :core:ui:assembleDebug -Pbrand=foxtrot`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Verify запрет неизвестного бренда**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :core:ui:assembleDebug -Pbrand=xxx`
Expected: BUILD FAIL с сообщением `Unknown brand: xxx. Allowed: [foxtrot, tango, sierra, kilo, love]`.

- [ ] **Step 6: Commit**

```bash
git add core/ui/build.gradle.kts core/ui/src/main/java/com/example/template/core/ui/AppBrand.kt core/ui/src/main/java/com/example/template/core/ui/AppTheme.kt
git commit -m "feat(theme): add AppBrand and AppTheme with build-time brand selection"
```

---

## Section C — Domain models (`:core:model`)

### Task C1: Базовые типы (Avatar, Status)

**Files:**
- Modify: `core/model/src/main/java/com/example/template/core/model/Placeholder.kt` (удалить)
- Create: `core/model/src/main/java/com/example/template/core/model/Avatar.kt`
- Create: `core/model/src/main/java/com/example/template/core/model/Status.kt`

- [ ] **Step 1: Удалить Placeholder.kt**

Delete: `core/model/src/main/java/com/example/template/core/model/Placeholder.kt`

- [ ] **Step 2: `Avatar.kt`**

```kotlin
package com.example.template.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AvatarSpec(
    val type: AvatarType,
    val initials: String? = null,
    val gradientIndex: Int = 0,
)

@Serializable
enum class AvatarType { Person, Group, Channel, Workspace, Self }
```

- [ ] **Step 3: `Status.kt`**

```kotlin
package com.example.template.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class UserStatus {
    @Serializable @SerialName("online")
    data object Online : UserStatus()

    @Serializable @SerialName("offline")
    data object Offline : UserStatus()

    @Serializable @SerialName("last_seen")
    data class LastSeen(val timestamp: Long) : UserStatus()
}

@Serializable
enum class MessageStatus { NONE, SENDING, DELIVERED, READ, ERROR }
```

- [ ] **Step 4: Verify build**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :core:model:assemble`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/model/
git commit -m "feat(model): add AvatarSpec, AvatarType, UserStatus, MessageStatus"
```

---

### Task C2: User, Space, CurrentUser

**Files:**
- Create: `core/model/src/main/java/com/example/template/core/model/User.kt`
- Create: `core/model/src/main/java/com/example/template/core/model/Space.kt`

- [ ] **Step 1: `User.kt`**

```kotlin
package com.example.template.core.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val avatar: AvatarSpec,
    val status: UserStatus = UserStatus.Offline,
)

@Serializable
data class CurrentUser(
    val id: String,
    val name: String,
    val avatar: AvatarSpec,
    val statusMessage: String? = null,
    val email: String? = null,
    val phone: String? = null,
)
```

- [ ] **Step 2: `Space.kt`**

```kotlin
package com.example.template.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Space(
    val id: String,
    val name: String,
    val avatar: AvatarSpec,
    val accentIndex: Int = 0,
)
```

- [ ] **Step 3: Commit**

```bash
git add core/model/src/main/java/com/example/template/core/model/User.kt core/model/src/main/java/com/example/template/core/model/Space.kt
git commit -m "feat(model): add User, CurrentUser, Space"
```

---

### Task C3: Chat и MessagePreview

**Files:**
- Create: `core/model/src/main/java/com/example/template/core/model/Chat.kt`

- [ ] **Step 1: `Chat.kt`**

```kotlin
package com.example.template.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChatType { P2P, Group, Channel }

@Serializable
enum class PreviewKind { Text, Group, Draft, Typing }

@Serializable
data class MessagePreview(
    val text: String,
    val kind: PreviewKind = PreviewKind.Text,
    val timestamp: Long,
    val ownStatus: MessageStatus = MessageStatus.NONE,
)

@Serializable
data class Chat(
    val id: String,
    val type: ChatType,
    val title: String,
    val avatar: AvatarSpec,
    val spaceId: String? = null,
    val participantIds: List<String> = emptyList(),
    val lastMessage: MessagePreview,
    val unreadCount: Int = 0,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val mention: Boolean = false,
    val hasReaction: Boolean = false,
)
```

- [ ] **Step 2: Commit**

```bash
git add core/model/src/main/java/com/example/template/core/model/Chat.kt
git commit -m "feat(model): add ChatType, Chat, MessagePreview"
```

---

### Task C4: Message (sealed) и связанные

**Files:**
- Create: `core/model/src/main/java/com/example/template/core/model/Message.kt`

- [ ] **Step 1: `Message.kt`**

```kotlin
package com.example.template.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Reaction(val emoji: String, val count: Int, val isMine: Boolean = false)

@Serializable
enum class AttachmentType { Photo, Video, File }

@Serializable
data class MediaAttachment(
    val placeholderColor: String,
    val type: AttachmentType,
    val durationMs: Long? = null,
)

@Serializable
enum class CallStatus { Answered, Rejected, Missed, NoAnswer }

@Serializable
enum class SpanStyle { Bold, Italic, Underline, Strikethrough, Mono, Link }

@Serializable
data class TextSpan(val start: Int, val endExclusive: Int, val style: SpanStyle)

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
    data class Text(
        override val id: String,
        override val chatId: String,
        override val senderId: String,
        override val timestamp: Long,
        override val isMine: Boolean,
        override val status: MessageStatus = MessageStatus.NONE,
        override val reactions: List<Reaction> = emptyList(),
        override val replyToId: String? = null,
        val body: String,
        val formatting: List<TextSpan> = emptyList(),
    ) : Message()

    @Serializable @SerialName("media")
    data class Media(
        override val id: String,
        override val chatId: String,
        override val senderId: String,
        override val timestamp: Long,
        override val isMine: Boolean,
        override val status: MessageStatus = MessageStatus.NONE,
        override val reactions: List<Reaction> = emptyList(),
        override val replyToId: String? = null,
        val attachments: List<MediaAttachment>,
        val caption: String? = null,
    ) : Message()

    @Serializable @SerialName("voice")
    data class Voice(
        override val id: String,
        override val chatId: String,
        override val senderId: String,
        override val timestamp: Long,
        override val isMine: Boolean,
        override val status: MessageStatus = MessageStatus.NONE,
        override val reactions: List<Reaction> = emptyList(),
        override val replyToId: String? = null,
        val durationMs: Long,
        val waveform: List<Int>,
        val transcription: String? = null,
    ) : Message()

    @Serializable @SerialName("link")
    data class Link(
        override val id: String,
        override val chatId: String,
        override val senderId: String,
        override val timestamp: Long,
        override val isMine: Boolean,
        override val status: MessageStatus = MessageStatus.NONE,
        override val reactions: List<Reaction> = emptyList(),
        override val replyToId: String? = null,
        val url: String,
        val title: String,
        val description: String? = null,
        val imageUrl: String? = null,
        val body: String? = null,
    ) : Message()

    @Serializable @SerialName("callmeet")
    data class CallMeet(
        override val id: String,
        override val chatId: String,
        override val senderId: String,
        override val timestamp: Long,
        override val isMine: Boolean,
        override val status: MessageStatus = MessageStatus.NONE,
        override val reactions: List<Reaction> = emptyList(),
        override val replyToId: String? = null,
        val callStatus: CallStatus,
        val isVideo: Boolean,
        val isGroupCall: Boolean,
        val durationMs: Long? = null,
    ) : Message()
}
```

- [ ] **Step 2: Verify build**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :core:model:assemble`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/model/src/main/java/com/example/template/core/model/Message.kt
git commit -m "feat(model): add Message sealed class with 5 subtypes"
```

---

### Task C5: Call

**Files:**
- Create: `core/model/src/main/java/com/example/template/core/model/Call.kt`

- [ ] **Step 1: `Call.kt`**

```kotlin
package com.example.template.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class CallType { Incoming, Outgoing, Missed }

@Serializable
data class Call(
    val id: String,
    val type: CallType,
    val counterpartId: String,
    val counterpartName: String,
    val counterpartAvatar: AvatarSpec,
    val isVideo: Boolean = false,
    val isGroupCall: Boolean = false,
    val timestamp: Long,
    val durationMs: Long? = null,
)
```

- [ ] **Step 2: Commit**

```bash
git add core/model/src/main/java/com/example/template/core/model/Call.kt
git commit -m "feat(model): add Call and CallType"
```

---

## Section D — Data layer (`:core:data`)

### Task D1: JsonModule (центральный Json instance)

**Files:**
- Create: `core/data/src/main/java/com/example/template/core/data/JsonModule.kt`

- [ ] **Step 1: `JsonModule.kt`**

```kotlin
package com.example.template.core.data

import kotlinx.serialization.json.Json

internal val AppJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}
```

- [ ] **Step 2: Commit**

```bash
git add core/data/src/main/java/com/example/template/core/data/JsonModule.kt
git commit -m "feat(data): add central kotlinx-serialization Json instance"
```

---

### Task D2: TDD — парсинг Message-полиморфного JSON

**Files:**
- Create: `core/data/src/test/java/com/example/template/core/data/JsonParsingTest.kt`

- [ ] **Step 1: Написать тест на парсинг Message.Text из JSON**

```kotlin
package com.example.template.core.data

import com.example.template.core.model.Message
import com.example.template.core.model.MessageStatus
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonParsingTest {

    private val json = AppJson

    @Test
    fun `parses Message Text via type discriminator`() {
        val src = """
            {"type":"text","id":"m-1","chatId":"c-1","senderId":"u-1","timestamp":1715000000000,
             "isMine":false,"status":"NONE","reactions":[],"replyToId":null,"body":"Hi","formatting":[]}
        """.trimIndent()
        val msg = json.decodeFromString<Message>(src)
        assertTrue(msg is Message.Text)
        assertEquals("Hi", msg.body)
    }

    @Test
    fun `parses Message Voice`() {
        val src = """
            {"type":"voice","id":"m-2","chatId":"c-1","senderId":"u-1","timestamp":1,
             "isMine":true,"status":"READ","reactions":[],"replyToId":null,
             "durationMs":4200,"waveform":[1,2,3]}
        """.trimIndent()
        val msg = json.decodeFromString<Message>(src)
        assertTrue(msg is Message.Voice)
        assertEquals(4200L, msg.durationMs)
        assertEquals(MessageStatus.READ, msg.status)
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :core:data:testDebugUnitTest --tests "com.example.template.core.data.JsonParsingTest"`
Expected: FAIL (если AppJson configuration не правильно настроен — править).

Если PASS — это уже верно, потому что Task D1 сделал конфиг правильно.

- [ ] **Step 3: Если FAIL — починить JsonModule**

Тип ошибки покажет, какая настройка отсутствует. Обычно — `classDiscriminator = "type"`.

- [ ] **Step 4: Run test — expect PASS**

Run: same.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/data/src/test/java/com/example/template/core/data/JsonParsingTest.kt
git commit -m "test(data): parse polymorphic Message JSON via type discriminator"
```

---

### Task D3: MessengerRepository interface

**Files:**
- Create: `core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt`

- [ ] **Step 1: `MessengerRepository.kt`**

```kotlin
package com.example.template.core.data

import com.example.template.core.model.Call
import com.example.template.core.model.Chat
import com.example.template.core.model.CurrentUser
import com.example.template.core.model.Message
import com.example.template.core.model.Space
import com.example.template.core.model.User
import kotlinx.coroutines.flow.StateFlow

interface MessengerRepository {
    val currentUser: StateFlow<CurrentUser>
    val users: StateFlow<List<User>>
    val spaces: StateFlow<List<Space>>
    val currentSpaceId: StateFlow<String>
    fun setCurrentSpace(id: String)

    val p2pChats: StateFlow<List<Chat>>
    fun spaceChats(spaceId: String): StateFlow<List<Chat>>

    val calls: StateFlow<List<Call>>

    suspend fun loadMessages(chatId: String): StateFlow<List<Message>>
    suspend fun sendTextMessage(chatId: String, body: String)

    fun getUser(id: String): User?
    fun getChat(id: String): Chat?
}
```

- [ ] **Step 2: Commit**

```bash
git add core/data/src/main/java/com/example/template/core/data/MessengerRepository.kt
git commit -m "feat(data): add MessengerRepository interface"
```

---

### Task D4: MockRepositoryImpl skeleton + JSON loading

**Files:**
- Create: `core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt`

- [ ] **Step 1: `MockRepositoryImpl.kt`**

```kotlin
package com.example.template.core.data

import android.content.Context
import com.example.template.core.model.Call
import com.example.template.core.model.Chat
import com.example.template.core.model.ChatType
import com.example.template.core.model.CurrentUser
import com.example.template.core.model.Message
import com.example.template.core.model.MessageStatus
import com.example.template.core.model.Space
import com.example.template.core.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class MockRepositoryImpl(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : MessengerRepository {

    private val _currentUser = MutableStateFlow(loadCurrentUser())
    override val currentUser: StateFlow<CurrentUser> = _currentUser.asStateFlow()

    private val _users = MutableStateFlow(loadList<User>("mock/users.json"))
    override val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _spaces = MutableStateFlow(loadList<Space>("mock/spaces.json"))
    override val spaces: StateFlow<List<Space>> = _spaces.asStateFlow()

    private val _currentSpaceId = MutableStateFlow(_spaces.value.firstOrNull()?.id.orEmpty())
    override val currentSpaceId: StateFlow<String> = _currentSpaceId.asStateFlow()

    private val _chats = MutableStateFlow(loadList<Chat>("mock/chats.json"))

    override val p2pChats: StateFlow<List<Chat>> =
        _chats.map { all -> all.filter { it.type == ChatType.P2P } }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    override fun spaceChats(spaceId: String): StateFlow<List<Chat>> =
        _chats.map { all -> all.filter { it.spaceId == spaceId && it.type != ChatType.P2P } }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    private val _calls = MutableStateFlow(loadList<Call>("mock/calls.json"))
    override val calls: StateFlow<List<Call>> = _calls.asStateFlow()

    private val messageCache = mutableMapOf<String, MutableStateFlow<List<Message>>>()

    override fun setCurrentSpace(id: String) {
        _currentSpaceId.value = id
    }

    override fun getUser(id: String): User? = _users.value.firstOrNull { it.id == id }
    override fun getChat(id: String): Chat? = _chats.value.firstOrNull { it.id == id }

    override suspend fun loadMessages(chatId: String): StateFlow<List<Message>> =
        withContext(Dispatchers.IO) {
            messageCache.getOrPut(chatId) {
                MutableStateFlow(loadList<Message>("mock/messages/$chatId.json"))
            }
        }

    override suspend fun sendTextMessage(chatId: String, body: String) {
        val cache = messageCache[chatId] ?: return
        val mine = _currentUser.value
        val newMsg = Message.Text(
            id = "m-${System.currentTimeMillis()}",
            chatId = chatId,
            senderId = mine.id,
            timestamp = System.currentTimeMillis(),
            isMine = true,
            status = MessageStatus.SENDING,
            body = body,
        )
        cache.value = cache.value + newMsg
        updateChatLastMessage(chatId, newMsg)
        scope.launch {
            delay(400)
            cache.update(newMsg.id) { it.copy(status = MessageStatus.DELIVERED) }
            delay(800)
            cache.update(newMsg.id) { it.copy(status = MessageStatus.READ) }
        }
    }

    private fun MutableStateFlow<List<Message>>.update(id: String, block: (Message.Text) -> Message.Text) {
        value = value.map { if (it.id == id && it is Message.Text) block(it) else it }
    }

    private fun updateChatLastMessage(chatId: String, msg: Message.Text) {
        _chats.value = _chats.value.map { chat ->
            if (chat.id == chatId) chat.copy(
                lastMessage = com.example.template.core.model.MessagePreview(
                    text = msg.body,
                    kind = com.example.template.core.model.PreviewKind.Text,
                    timestamp = msg.timestamp,
                    ownStatus = msg.status,
                )
            ) else chat
        }
    }

    private fun loadCurrentUser(): CurrentUser =
        AppJson.decodeFromString(readAsset("mock/current-user.json"))

    private inline fun <reified T> loadList(asset: String): List<T> =
        AppJson.decodeFromString(readAsset(asset))

    private fun readAsset(path: String): String = runBlocking(Dispatchers.IO) {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }
}
```

- [ ] **Step 2: Verify build**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :core:data:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt
git commit -m "feat(data): add MockRepositoryImpl reading JSON from assets"
```

---

### Task D5: TDD — фильтрация p2pChats и spaceChats

**Files:**
- Create: `core/data/src/test/java/com/example/template/core/data/RepositoryFilterTest.kt`

Поскольку `MockRepositoryImpl` зависит от `Context.assets`, юнит-тест на чистую логику фильтра проще написать через mini-функцию или Robolectric. Для прототипа — выделим pure-функцию.

- [ ] **Step 1: Извлечь pure-функции фильтрации**

Modify `MockRepositoryImpl.kt`: фильтры внутри `p2pChats` и `spaceChats` уже используют простые предикаты. Для тестируемости можно вынести в companion:

```kotlin
companion object {
    fun filterP2P(chats: List<Chat>): List<Chat> = chats.filter { it.type == ChatType.P2P }
    fun filterSpace(chats: List<Chat>, spaceId: String): List<Chat> =
        chats.filter { it.spaceId == spaceId && it.type != ChatType.P2P }
}
```

Использовать эти функции внутри `.map { ... }` блоков.

- [ ] **Step 2: Написать тест**

```kotlin
package com.example.template.core.data

import com.example.template.core.model.AvatarSpec
import com.example.template.core.model.AvatarType
import com.example.template.core.model.Chat
import com.example.template.core.model.ChatType
import com.example.template.core.model.MessagePreview
import com.example.template.core.model.PreviewKind
import org.junit.Test
import kotlin.test.assertEquals

class RepositoryFilterTest {

    private fun chat(id: String, type: ChatType, spaceId: String?) = Chat(
        id = id, type = type, title = id,
        avatar = AvatarSpec(AvatarType.Person, initials = id),
        spaceId = spaceId, lastMessage = MessagePreview("", PreviewKind.Text, 0L),
    )

    @Test
    fun `filterP2P returns only P2P chats`() {
        val all = listOf(
            chat("1", ChatType.P2P, null),
            chat("2", ChatType.Group, "s1"),
            chat("3", ChatType.Channel, "s1"),
            chat("4", ChatType.P2P, null),
        )
        val p2p = MockRepositoryImpl.filterP2P(all)
        assertEquals(listOf("1", "4"), p2p.map { it.id })
    }

    @Test
    fun `filterSpace returns Group and Channel of given space`() {
        val all = listOf(
            chat("g1", ChatType.Group, "s1"),
            chat("c1", ChatType.Channel, "s1"),
            chat("g2", ChatType.Group, "s2"),
            chat("p", ChatType.P2P, null),
        )
        val s1 = MockRepositoryImpl.filterSpace(all, "s1")
        assertEquals(listOf("g1", "c1"), s1.map { it.id })
    }
}
```

- [ ] **Step 3: Run test — expect PASS**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :core:data:testDebugUnitTest`
Expected: PASS (3 tests).

- [ ] **Step 4: Commit**

```bash
git add core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt core/data/src/test/java/com/example/template/core/data/RepositoryFilterTest.kt
git commit -m "test(data): cover P2P and space chat filters"
```

---

### Task D6: Mock JSON-данные

**Files:**
- Create: `app/src/main/assets/mock/current-user.json`
- Create: `app/src/main/assets/mock/users.json`
- Create: `app/src/main/assets/mock/spaces.json`
- Create: `app/src/main/assets/mock/chats.json`
- Create: `app/src/main/assets/mock/calls.json`
- Create: `app/src/main/assets/mock/messages/chat-001.json`
- Create: `app/src/main/assets/mock/messages/chat-002.json`
- Create: `app/src/main/assets/mock/messages/chat-003.json`

Объём данных: ~3-5 пользователей, ~2 пространства, ~6-8 чатов (mix P2P/Group/Channel), ~3-5 звонков, по 5-10 сообщений на 3 чата.

- [ ] **Step 1: `current-user.json`**

```json
{
  "id": "u-self",
  "name": "Иван Петров",
  "avatar": {"type": "Self", "initials": "ИП", "gradientIndex": 0},
  "statusMessage": "Доступен",
  "email": "ivan.petrov@example.com",
  "phone": "+7 999 123-45-67"
}
```

- [ ] **Step 2: `users.json`**

```json
[
  {"id": "u-1", "name": "Мария Климова", "avatar": {"type": "Person", "initials": "МК", "gradientIndex": 1}, "status": {"type": "online"}},
  {"id": "u-2", "name": "Павел Сердюков", "avatar": {"type": "Person", "initials": "ПС", "gradientIndex": 2}, "status": {"type": "last_seen", "timestamp": 1715600000000}},
  {"id": "u-3", "name": "Анна Ковалёва", "avatar": {"type": "Person", "initials": "АК", "gradientIndex": 3}, "status": {"type": "online"}},
  {"id": "u-4", "name": "Дмитрий Федоров", "avatar": {"type": "Person", "initials": "ДФ", "gradientIndex": 4}, "status": {"type": "offline"}},
  {"id": "u-5", "name": "Николай Павлов", "avatar": {"type": "Person", "initials": "НП", "gradientIndex": 0}, "status": {"type": "offline"}}
]
```

- [ ] **Step 3: `spaces.json`**

```json
[
  {"id": "sp-1", "name": "Команда продукта", "avatar": {"type": "Workspace", "initials": "КП", "gradientIndex": 0}, "accentIndex": 0},
  {"id": "sp-2", "name": "Маркетинг", "avatar": {"type": "Workspace", "initials": "МК", "gradientIndex": 2}, "accentIndex": 1}
]
```

- [ ] **Step 4: `chats.json`**

```json
[
  {"id": "chat-001", "type": "P2P", "title": "Мария Климова", "avatar": {"type": "Person", "initials": "МК", "gradientIndex": 1},
   "spaceId": null, "participantIds": ["u-1"],
   "lastMessage": {"text": "Отправила материалы", "kind": "Text", "timestamp": 1715600400000, "ownStatus": "NONE"},
   "unreadCount": 0, "pinned": true},

  {"id": "chat-002", "type": "P2P", "title": "Павел Сердюков", "avatar": {"type": "Person", "initials": "ПС", "gradientIndex": 2},
   "spaceId": null, "participantIds": ["u-2"],
   "lastMessage": {"text": "Всё верно", "kind": "Text", "timestamp": 1715600300000, "ownStatus": "NONE"},
   "unreadCount": 1},

  {"id": "chat-003", "type": "P2P", "title": "Анна Ковалёва", "avatar": {"type": "Person", "initials": "АК", "gradientIndex": 3},
   "spaceId": null, "participantIds": ["u-3"],
   "lastMessage": {"text": "Добрый день", "kind": "Text", "timestamp": 1715600200000, "ownStatus": "NONE"}},

  {"id": "chat-101", "type": "Group", "title": "Аналитика и контроль", "avatar": {"type": "Group", "initials": "АК", "gradientIndex": 4},
   "spaceId": "sp-1", "participantIds": ["u-1", "u-2", "u-3"],
   "lastMessage": {"text": "Я ознакомился с отчётами", "kind": "Group", "timestamp": 1715600100000, "ownStatus": "NONE"},
   "unreadCount": 3},

  {"id": "chat-102", "type": "Channel", "title": "Новости компании", "avatar": {"type": "Channel", "initials": "НК", "gradientIndex": 0},
   "spaceId": "sp-1", "participantIds": [],
   "lastMessage": {"text": "Запуск нового продукта", "kind": "Text", "timestamp": 1715599800000, "ownStatus": "NONE"}},

  {"id": "chat-201", "type": "Group", "title": "Маркетинг команда", "avatar": {"type": "Group", "initials": "МК", "gradientIndex": 2},
   "spaceId": "sp-2", "participantIds": ["u-4", "u-5"],
   "lastMessage": {"text": "Готовлю презентацию", "kind": "Group", "timestamp": 1715599500000, "ownStatus": "NONE"}}
]
```

- [ ] **Step 5: `calls.json`**

```json
[
  {"id": "call-1", "type": "Incoming", "counterpartId": "u-1", "counterpartName": "Мария Климова",
   "counterpartAvatar": {"type": "Person", "initials": "МК", "gradientIndex": 1},
   "isVideo": false, "isGroupCall": false, "timestamp": 1715600400000, "durationMs": 180000},
  {"id": "call-2", "type": "Missed", "counterpartId": "u-2", "counterpartName": "Павел Сердюков",
   "counterpartAvatar": {"type": "Person", "initials": "ПС", "gradientIndex": 2},
   "isVideo": false, "isGroupCall": false, "timestamp": 1715600300000},
  {"id": "call-3", "type": "Outgoing", "counterpartId": "chat-101", "counterpartName": "Аналитика и контроль",
   "counterpartAvatar": {"type": "Group", "initials": "АК", "gradientIndex": 4},
   "isVideo": true, "isGroupCall": true, "timestamp": 1715600200000, "durationMs": 1200000}
]
```

- [ ] **Step 6: `messages/chat-001.json`**

```json
[
  {"type": "text", "id": "m-001-1", "chatId": "chat-001", "senderId": "u-1", "timestamp": 1715600000000,
   "isMine": false, "status": "NONE", "reactions": [{"emoji": "👍", "count": 1, "isMine": false}], "replyToId": null,
   "body": "Привет! Как дела?", "formatting": []},
  {"type": "text", "id": "m-001-2", "chatId": "chat-001", "senderId": "u-self", "timestamp": 1715600100000,
   "isMine": true, "status": "READ", "reactions": [], "replyToId": null,
   "body": "Привет, всё отлично", "formatting": []},
  {"type": "voice", "id": "m-001-3", "chatId": "chat-001", "senderId": "u-1", "timestamp": 1715600200000,
   "isMine": false, "status": "NONE", "reactions": [], "replyToId": null,
   "durationMs": 4200, "waveform": [2,4,6,8,6,4,2,3,5,7,5,3], "transcription": null},
  {"type": "text", "id": "m-001-4", "chatId": "chat-001", "senderId": "u-1", "timestamp": 1715600300000,
   "isMine": false, "status": "NONE", "reactions": [], "replyToId": null,
   "body": "Отправила материалы для итогового отчёта", "formatting": []}
]
```

- [ ] **Step 7: `messages/chat-002.json` и `messages/chat-003.json`**

Аналогично — по 3-5 текстовых сообщений в каждом, с микса `isMine`. Использовать тот же формат, что и `chat-001.json`.

- [ ] **Step 8: Verify — build app**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Assets попадают в APK.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/assets/
git commit -m "feat(data): add mock JSON for users, spaces, chats, calls, messages"
```

---

### Task D7: TDD — sendTextMessage с status-переходами

**Files:**
- Create: `core/data/src/test/java/com/example/template/core/data/SendMessageTest.kt`

- [ ] **Step 1: Извлечь pure status-transition helper**

В `MockRepositoryImpl.kt` уже есть extension `update(id, block)`. Также эта логика testable, если abstract loading of assets вынесем.

Для prototype-теста используем integrated unit-тест через `Robolectric` — НО это weight. Альтернатива: unit-тест на pure-функции цепочки переходов.

Извлечь pure-функцию `mockStatusTransitions`:

```kotlin
internal object StatusTransitions {
    val sequence = listOf(MessageStatus.SENDING, MessageStatus.DELIVERED, MessageStatus.READ)
    val intervalsMs = listOf(400L, 800L)
}
```

Использовать в `sendTextMessage`:

```kotlin
override suspend fun sendTextMessage(chatId: String, body: String) {
    // ... create newMsg ...
    cache.value = cache.value + newMsg
    updateChatLastMessage(chatId, newMsg)
    scope.launch {
        StatusTransitions.intervalsMs.forEachIndexed { idx, ms ->
            delay(ms)
            val nextStatus = StatusTransitions.sequence[idx + 1]
            cache.update(newMsg.id) { it.copy(status = nextStatus) }
            updateChatLastMessage(chatId, cache.value.first { it.id == newMsg.id } as Message.Text)
        }
    }
}
```

- [ ] **Step 2: Тест на StatusTransitions invariants**

```kotlin
package com.example.template.core.data

import com.example.template.core.model.MessageStatus
import org.junit.Test
import kotlin.test.assertEquals

class SendMessageTest {
    @Test
    fun `status sequence is SENDING DELIVERED READ`() {
        assertEquals(
            listOf(MessageStatus.SENDING, MessageStatus.DELIVERED, MessageStatus.READ),
            StatusTransitions.sequence,
        )
    }

    @Test
    fun `intervals match sequence transitions count`() {
        assertEquals(
            StatusTransitions.sequence.size - 1,
            StatusTransitions.intervalsMs.size,
        )
    }
}
```

- [ ] **Step 3: Run tests**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :core:data:testDebugUnitTest`
Expected: PASS (все тесты).

- [ ] **Step 4: Commit**

```bash
git add core/data/src/main/java/com/example/template/core/data/MockRepositoryImpl.kt core/data/src/test/java/com/example/template/core/data/SendMessageTest.kt
git commit -m "test(data): verify status transition sequence invariants"
```

---

## Section E — Navigation (`:core:navigation`)

### Task E1: NavRoute, TabRoute, NavExtensions

**Files:**
- Modify: `core/navigation/src/main/java/.../Placeholder.kt` (удалить)
- Create: `core/navigation/src/main/java/com/example/template/core/navigation/NavRoute.kt`
- Create: `core/navigation/src/main/java/com/example/template/core/navigation/TabRoute.kt`
- Create: `core/navigation/src/main/java/com/example/template/core/navigation/NavExtensions.kt`

- [ ] **Step 1: Удалить Placeholder.kt**

Delete: `core/navigation/src/main/java/com/example/template/core/navigation/Placeholder.kt`

- [ ] **Step 2: `NavRoute.kt`**

```kotlin
package com.example.template.core.navigation

sealed class NavRoute(val route: String) {
    data object Main : NavRoute("main")
    data class ChatDetail(val chatId: String) : NavRoute("chat/$chatId") {
        companion object {
            const val PATTERN = "chat/{chatId}"
            const val ARG_CHAT_ID = "chatId"
        }
    }
}
```

- [ ] **Step 3: `TabRoute.kt`**

```kotlin
package com.example.template.core.navigation

sealed class TabRoute(val route: String) {
    data object Chats   : TabRoute("tab/chats")
    data object Spaces  : TabRoute("tab/spaces")
    data object Calls   : TabRoute("tab/calls")
    data object Profile : TabRoute("tab/profile")

    companion object {
        val all = listOf(Chats, Spaces, Calls, Profile)
    }
}
```

- [ ] **Step 4: `NavExtensions.kt`**

```kotlin
package com.example.template.core.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

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

- [ ] **Step 5: Verify build**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :core:navigation:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add core/navigation/
git commit -m "feat(navigation): add NavRoute, TabRoute, navigation extensions"
```

---

## Section F — UI primitives (`:core:ui`)

### Task F1: AppScaffold

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/AppScaffold.kt`

- [ ] **Step 1: `AppScaffold.kt`**

```kotlin
package com.example.template.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun AppScaffold(
    content: @Composable () -> Unit,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(brand.backgroundBase(isDark)))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        content()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/AppScaffold.kt
git commit -m "feat(ui): add AppScaffold with brand-aware background and insets"
```

---

### Task F2: HeaderHost — обёртка над HeadersView

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/hosts/HeaderHost.kt`

- [ ] **Step 1: `HeaderHost.kt`**

```kotlin
package com.example.template.core.ui.hosts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.designsystem.DSBrand
import com.example.components.headers.HeadersColorScheme
import com.example.components.headers.HeadersView
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark

@Composable
fun HeaderHost(
    config: HeadersView.HeaderConfig,
    modifier: Modifier = Modifier,
) {
    val brand: DSBrand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    AndroidView(
        modifier = modifier,
        factory = { ctx -> HeadersView(ctx) },
        update = { view ->
            view.configure(config, DSBrand.headersColorScheme(brand, isDark))
        },
    )
}
```

**ВАЖНО:** функция `DSBrand.headersColorScheme(brand, isDark)` — это static helper в companion object DSBrand или extension. Engineer должен проверить точное API в `DSBrand.kt` (`headersColorScheme(isDark)` через enum-method или через companion). Если метод инстанс-уровня: `brand.headersColorScheme(isDark)`. Прим.: в android-components README паттерн `DSBrand.<component>ColorScheme(isDark)` — это, вероятно, instance-метод enum, т.е. `brand.headersColorScheme(isDark)`. Скорректировать соответственно.

- [ ] **Step 2: Verify build**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :core:ui:assembleDebug`
Expected: BUILD SUCCESSFUL. Если падает на `headersColorScheme` — посмотреть `DSBrand.kt` и подкорректировать вызов.

- [ ] **Step 3: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/HeaderHost.kt
git commit -m "feat(ui): add HeaderHost Compose adapter for HeadersView"
```

---

### Task F3: ChatListHost — список чатов

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/hosts/ChatListHost.kt`

- [ ] **Step 1: `ChatListHost.kt`**

```kotlin
package com.example.template.core.ui.hosts

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.chatlist.ChatListItemView
import com.example.template.core.model.Chat
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark

@Composable
fun ChatListHost(
    chats: List<Chat>,
    onChatClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    LazyColumn(modifier = modifier) {
        items(chats, key = { it.id }) { chat ->
            AndroidView(
                factory = { ctx ->
                    ChatListItemView(ctx).apply {
                        setOnClickListener { onChatClick(chat.id) }
                    }
                },
                update = { view ->
                    view.configure(
                        avatarText = chat.avatar.initials.orEmpty(),
                        title = chat.title,
                        // ... остальные поля из ChatListItemView.configure
                        // engineer: см. ChatListItemView.kt:446 для полной сигнатуры,
                        // мапить из Chat: avatar.type → AvatarMode, lastMessage → preview text,
                        // unreadCount → counter, pinned → pin badge, etc.
                    )
                    view.setOnClickListener { onChatClick(chat.id) }
                },
            )
        }
    }
}
```

**Engineer note:** `ChatListItemView.configure` имеет ~30+ параметров (см. ChatListItemView.kt:446). Маппинг доменных полей Chat → configure-параметров реализуется здесь. Делать по минимуму того, что отображается в макете (avatar, title, preview text, time, unread count, pin/mention badges). Color schemes из `brand.chatListColorScheme(isDark)`.

- [ ] **Step 2: Verify build**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :core:ui:assembleDebug`
Expected: BUILD SUCCESSFUL (после корректной мапки полей).

- [ ] **Step 3: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/ChatListHost.kt
git commit -m "feat(ui): add ChatListHost mapping Chat model to ChatListItemView"
```

---

### Task F4: BottomTabsHost

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/hosts/BottomTabsHost.kt`

- [ ] **Step 1: Прочитать `BottomTabsView.kt`**

Read: `../android-components/components/src/main/java/com/example/components/bottomtabs/BottomTabsView.kt`

Узнать сигнатуру configure() и формат tab config.

- [ ] **Step 2: `BottomTabsHost.kt`**

```kotlin
package com.example.template.core.ui.hosts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.components.bottomtabs.BottomTabsView
import com.example.template.core.navigation.TabRoute
import com.example.template.core.navigation.navigateToTab
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark

@Composable
fun BottomTabsHost(
    tabNavController: NavController,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    val backStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: TabRoute.Chats.route

    AndroidView(
        modifier = modifier,
        factory = { ctx -> BottomTabsView(ctx) },
        update = { view ->
            // engineer: вызвать view.configure(...) с 4 табами (Chats/Spaces/Calls/Profile),
            // selectedIndex = TabRoute.all.indexOfFirst { it.route == currentRoute },
            // onTabSelected = { idx -> tabNavController.navigateToTab(TabRoute.all[idx]) },
            // colorScheme = brand.bottomTabsColorScheme(isDark)
            // Точная сигнатура — в BottomTabsView.kt
        },
    )
}
```

**Engineer note:** BottomTabsView имеет встроенные иконки (ic-message-filled / ic-space-32 / ic-call-32 + avatar tab). Profile-таб может потребовать передачи Bitmap аватара текущего пользователя — см. сигнатуру configure().

- [ ] **Step 3: Verify build**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :core:ui:assembleDebug`
Expected: BUILD SUCCESSFUL после корректной мапки.

- [ ] **Step 4: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/BottomTabsHost.kt
git commit -m "feat(ui): add BottomTabsHost wired to nav controller"
```

---

### Task F5: MessageList — роутинг по типу сообщения

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt`

- [ ] **Step 1: `MessageList.kt`**

```kotlin
package com.example.template.core.ui.hosts

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.bubbles.BubblesView
import com.example.components.bubbles.LinkBubbleView
import com.example.components.bubbles.MediaBubbleView
import com.example.components.bubbles.VoiceBubbleView
import com.example.components.callmeet.CallMeetView
import com.example.template.core.model.Message
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark

@Composable
fun MessageList(
    messages: List<Message>,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    LazyColumn(modifier = modifier) {
        items(messages, key = { it.id }) { msg ->
            when (msg) {
                is Message.Text -> AndroidView(
                    factory = { BubblesView(it) },
                    update = { v ->
                        // engineer: v.configure(...) с body, isMine, status, timestamp, reactions
                        // см. BubblesView.kt для сигнатуры
                    },
                )
                is Message.Media -> AndroidView(
                    factory = { MediaBubbleView(it) },
                    update = { v -> /* configure */ },
                )
                is Message.Voice -> AndroidView(
                    factory = { VoiceBubbleView(it) },
                    update = { v -> /* configure */ },
                )
                is Message.Link -> AndroidView(
                    factory = { LinkBubbleView(it) },
                    update = { v -> /* configure */ },
                )
                is Message.CallMeet -> AndroidView(
                    factory = { CallMeetView(it) },
                    update = { v -> /* configure */ },
                )
            }
        }
    }
}
```

**Engineer note:** каждый bubble имеет свою `configure(...)` сигнатуру в `..View.kt`. Маппинг минимальный — то что нужно для отображения. Reactions показываются ReactionsView отдельным элементом под bubble (если `msg.reactions.isNotEmpty()`); это можно либо встроить здесь, либо вынести.

- [ ] **Step 2: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessageList.kt
git commit -m "feat(ui): add MessageList routing by Message subtype"
```

---

### Task F6: MessagePanelHost

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt`

- [ ] **Step 1: `MessagePanelHost.kt`**

```kotlin
package com.example.template.core.ui.hosts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.messagepanel.MessagePanelConfig
import com.example.components.messagepanel.MessagePanelView
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark

@Composable
fun MessagePanelHost(
    onSendText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MessagePanelView(ctx).apply {
                // engineer: установить callback на отправку текста
                // (см. список из 12 callbacks в MessagePanelView)
                // обычно onSendClick = { text -> onSendText(text) }
            }
        },
        update = { view ->
            view.configure(MessagePanelConfig(), /* colorScheme = brand.messagePanelColorScheme(isDark) */)
        },
    )
}
```

**Engineer note:** MessagePanelView имеет 12 callbacks (см. MessagePanelView.kt:617 для configure и поля колбэков). Wired для baseline — только text send.

- [ ] **Step 2: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/hosts/MessagePanelHost.kt
git commit -m "feat(ui): add MessagePanelHost wiring text send callback"
```

---

### Task F7: CallRow и ProfileRow

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/rows/CallRow.kt`
- Create: `core/ui/src/main/java/com/example/template/core/ui/rows/ProfileRow.kt`

- [ ] **Step 1: `CallRow.kt`**

```kotlin
package com.example.template.core.ui.rows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.avatar.AvatarView
import com.example.template.core.model.Call
import com.example.template.core.model.CallType
import com.example.template.core.ui.LocalAppBrand
import com.example.template.core.ui.LocalIsDark

@Composable
fun CallRow(
    call: Call,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalAppBrand.current
    val isDark = LocalIsDark.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        AndroidView(
            modifier = Modifier.size(48.dp),
            factory = { AvatarView(it) },
            update = { v ->
                // engineer: v.configure(...) с counterpartAvatar
            },
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(call.counterpartName, color = Color(brand.accentColor(isDark)))
            Text(callTypeLabel(call), color = Color(brand.accentColor(isDark)).copy(alpha = 0.5f))
        }
    }
}

private fun callTypeLabel(call: Call): String = when (call.type) {
    CallType.Incoming -> if (call.isVideo) "Входящий видеозвонок" else "Входящий"
    CallType.Outgoing -> if (call.isVideo) "Исходящий видеозвонок" else "Исходящий"
    CallType.Missed   -> "Пропущенный"
}
```

**Engineer note:** для production-визуала использовать `DSTypography.<style>.toComposeTextStyle()` вместо `Text(...)` без стиля. Цвета — из `<X>ColorScheme.from(brand, isDark)` подходящего компонента.

- [ ] **Step 2: `ProfileRow.kt`**

```kotlin
package com.example.template.core.ui.rows

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProfileRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title)
        Text(value)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/rows/
git commit -m "feat(ui): add CallRow and ProfileRow"
```

---

### Task F8: SpaceSwitcherSheet и EmptyState

**Files:**
- Create: `core/ui/src/main/java/com/example/template/core/ui/components/SpaceSwitcherSheet.kt`
- Create: `core/ui/src/main/java/com/example/template/core/ui/components/EmptyState.kt`

- [ ] **Step 1: `SpaceSwitcherSheet.kt`**

```kotlin
package com.example.template.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.avatar.AvatarView
import com.example.template.core.model.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceSwitcherSheet(
    spaces: List<Space>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column {
            spaces.forEach { space ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(space.id); onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    AndroidView(
                        modifier = Modifier.size(40.dp),
                        factory = { AvatarView(it) },
                        update = { /* configure with space.avatar */ },
                    )
                    Text(space.name, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}
```

- [ ] **Step 2: `EmptyState.kt`**

```kotlin
package com.example.template.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add core/ui/src/main/java/com/example/template/core/ui/components/
git commit -m "feat(ui): add SpaceSwitcherSheet and EmptyState"
```

---

## Section G — Feature modules

### Task G1: `:feature:chats` — ViewModel и Screen

**Files:**
- Create: `feature/chats/src/main/java/com/example/template/feature/chats/ChatsViewModel.kt`
- Create: `feature/chats/src/main/java/com/example/template/feature/chats/ChatsScreen.kt`
- Create: `feature/chats/src/main/java/com/example/template/feature/chats/ChatsNavGraph.kt`

- [ ] **Step 1: `ChatsViewModel.kt`**

```kotlin
package com.example.template.feature.chats

import androidx.lifecycle.ViewModel
import com.example.template.core.data.MessengerRepository
import com.example.template.core.model.Chat
import kotlinx.coroutines.flow.StateFlow

class ChatsViewModel(repository: MessengerRepository) : ViewModel() {
    val chats: StateFlow<List<Chat>> = repository.p2pChats
}
```

- [ ] **Step 2: `ChatsScreen.kt`**

```kotlin
package com.example.template.feature.chats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.components.headers.HeadersView
import com.example.template.core.ui.components.EmptyState
import com.example.template.core.ui.hosts.ChatListHost
import com.example.template.core.ui.hosts.HeaderHost

@Composable
fun ChatsScreen(
    viewModel: ChatsViewModel,
    onChatClick: (String) -> Unit,
) {
    val chats by viewModel.chats.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        HeaderHost(
            config = HeadersView.HeaderConfig.Main(
                mode = HeadersView.HeaderConfig.Main.Mode.CHATS,
                title = "Чаты",
                showSearch = true,
                onPlusClick = { /* no-op в scope */ },
            ),
        )
        if (chats.isEmpty()) {
            EmptyState("Нет личных чатов")
        } else {
            ChatListHost(chats = chats, onChatClick = onChatClick, modifier = Modifier.fillMaxSize())
        }
    }
}
```

- [ ] **Step 3: `ChatsNavGraph.kt`**

```kotlin
package com.example.template.feature.chats

import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.template.core.data.MessengerRepository
import com.example.template.core.navigation.TabRoute

fun NavGraphBuilder.chatsScreen(
    repository: MessengerRepository,
    onChatClick: (String) -> Unit,
) {
    composable(TabRoute.Chats.route) {
        val vm = remember { ChatsViewModel(repository) }
        ChatsScreen(viewModel = vm, onChatClick = onChatClick)
    }
}
```

- [ ] **Step 4: Verify build**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :feature:chats:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add feature/chats/
git commit -m "feat(chats): add ChatsScreen, ViewModel, NavGraph"
```

---

### Task G2: `:feature:spaces` — со switcher sheet

**Files:**
- Create: `feature/spaces/src/main/java/com/example/template/feature/spaces/SpacesViewModel.kt`
- Create: `feature/spaces/src/main/java/com/example/template/feature/spaces/SpacesScreen.kt`
- Create: `feature/spaces/src/main/java/com/example/template/feature/spaces/SpacesNavGraph.kt`

- [ ] **Step 1: `SpacesViewModel.kt`**

```kotlin
package com.example.template.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.template.core.data.MessengerRepository
import com.example.template.core.model.Chat
import com.example.template.core.model.Space
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SpacesViewModel(private val repository: MessengerRepository) : ViewModel() {
    val spaces: StateFlow<List<Space>> = repository.spaces
    val currentSpace: StateFlow<Space?> =
        repository.currentSpaceId
            .map { id -> repository.spaces.value.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val chats: StateFlow<List<Chat>> =
        repository.currentSpaceId
            .flatMapLatest { id -> repository.spaceChats(id) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setSpace(id: String) = repository.setCurrentSpace(id)
}
```

- [ ] **Step 2: `SpacesScreen.kt`**

```kotlin
package com.example.template.feature.spaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.components.headers.HeadersView
import com.example.template.core.ui.components.EmptyState
import com.example.template.core.ui.components.SpaceSwitcherSheet
import com.example.template.core.ui.hosts.ChatListHost
import com.example.template.core.ui.hosts.HeaderHost

@Composable
fun SpacesScreen(
    viewModel: SpacesViewModel,
    onChatClick: (String) -> Unit,
) {
    val spaces by viewModel.spaces.collectAsState()
    val current by viewModel.currentSpace.collectAsState()
    val chats by viewModel.chats.collectAsState()
    var showSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderHost(
            config = HeadersView.HeaderConfig.Main(
                mode = HeadersView.HeaderConfig.Main.Mode.SPACES,
                title = current?.name.orEmpty(),
                showSearch = true,
                onGroupClick = { showSheet = true },
                onPlusClick = { /* no-op */ },
            ),
        )
        if (chats.isEmpty()) {
            EmptyState("В этом пространстве нет чатов")
        } else {
            ChatListHost(chats = chats, onChatClick = onChatClick, modifier = Modifier.fillMaxSize())
        }
    }

    if (showSheet) {
        SpaceSwitcherSheet(
            spaces = spaces,
            onSelect = { viewModel.setSpace(it) },
            onDismiss = { showSheet = false },
        )
    }
}
```

- [ ] **Step 3: `SpacesNavGraph.kt`**

```kotlin
package com.example.template.feature.spaces

import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.template.core.data.MessengerRepository
import com.example.template.core.navigation.TabRoute

fun NavGraphBuilder.spacesScreen(
    repository: MessengerRepository,
    onChatClick: (String) -> Unit,
) {
    composable(TabRoute.Spaces.route) {
        val vm = remember { SpacesViewModel(repository) }
        SpacesScreen(viewModel = vm, onChatClick = onChatClick)
    }
}
```

- [ ] **Step 4: Verify build**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :feature:spaces:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add feature/spaces/
git commit -m "feat(spaces): add SpacesScreen with space switcher bottom sheet"
```

---

### Task G3: `:feature:calls`

**Files:**
- Create: `feature/calls/src/main/java/com/example/template/feature/calls/CallsViewModel.kt`
- Create: `feature/calls/src/main/java/com/example/template/feature/calls/CallsScreen.kt`
- Create: `feature/calls/src/main/java/com/example/template/feature/calls/CallsNavGraph.kt`

- [ ] **Step 1: `CallsViewModel.kt`**

```kotlin
package com.example.template.feature.calls

import androidx.lifecycle.ViewModel
import com.example.template.core.data.MessengerRepository
import com.example.template.core.model.Call
import kotlinx.coroutines.flow.StateFlow

class CallsViewModel(repository: MessengerRepository) : ViewModel() {
    val calls: StateFlow<List<Call>> = repository.calls
}
```

- [ ] **Step 2: `CallsScreen.kt`**

```kotlin
package com.example.template.feature.calls

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.components.headers.HeadersView
import com.example.template.core.ui.components.EmptyState
import com.example.template.core.ui.hosts.HeaderHost
import com.example.template.core.ui.rows.CallRow

@Composable
fun CallsScreen(viewModel: CallsViewModel) {
    val calls by viewModel.calls.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        HeaderHost(
            config = HeadersView.HeaderConfig.Custom(
                title = "Звонки",
                leftButton = HeadersView.HeaderConfig.Custom.LeftButton.HIDE,
                rightButton = HeadersView.HeaderConfig.Custom.RightButton.HIDE,
            ),
        )
        if (calls.isEmpty()) {
            EmptyState("История звонков пуста")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(calls, key = { it.id }) { call ->
                    CallRow(call = call, onClick = { /* no-op в scope */ })
                }
            }
        }
    }
}
```

- [ ] **Step 3: `CallsNavGraph.kt`**

```kotlin
package com.example.template.feature.calls

import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.template.core.data.MessengerRepository
import com.example.template.core.navigation.TabRoute

fun NavGraphBuilder.callsScreen(repository: MessengerRepository) {
    composable(TabRoute.Calls.route) {
        val vm = remember { CallsViewModel(repository) }
        CallsScreen(viewModel = vm)
    }
}
```

- [ ] **Step 4: Verify + commit**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :feature:calls:assembleDebug
git add feature/calls/
git commit -m "feat(calls): add CallsScreen with custom header"
```

---

### Task G4: `:feature:profile`

**Files:**
- Create: `feature/profile/src/main/java/com/example/template/feature/profile/ProfileViewModel.kt`
- Create: `feature/profile/src/main/java/com/example/template/feature/profile/ProfileScreen.kt`
- Create: `feature/profile/src/main/java/com/example/template/feature/profile/ProfileNavGraph.kt`

- [ ] **Step 1: `ProfileViewModel.kt`**

```kotlin
package com.example.template.feature.profile

import androidx.lifecycle.ViewModel
import com.example.template.core.data.MessengerRepository
import com.example.template.core.model.CurrentUser
import kotlinx.coroutines.flow.StateFlow

class ProfileViewModel(repository: MessengerRepository) : ViewModel() {
    val user: StateFlow<CurrentUser> = repository.currentUser
}
```

- [ ] **Step 2: `ProfileScreen.kt`**

```kotlin
package com.example.template.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.avatar.AvatarView
import com.example.components.headers.HeadersView
import com.example.template.core.ui.AppBrandCodename
import com.example.template.core.ui.hosts.HeaderHost
import com.example.template.core.ui.rows.ProfileRow

@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val user by viewModel.user.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        HeaderHost(
            config = HeadersView.HeaderConfig.Custom(
                title = "Профиль",
                leftButton = HeadersView.HeaderConfig.Custom.LeftButton.HIDE,
                rightButton = HeadersView.HeaderConfig.Custom.RightButton.HIDE,
            ),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AndroidView(
                modifier = Modifier.size(96.dp),
                factory = { AvatarView(it) },
                update = { /* configure with user.avatar */ },
            )
            Text(user.name, modifier = Modifier.padding(top = 12.dp))
            user.statusMessage?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
        }
        user.email?.let { ProfileRow(title = "Email", value = it) }
        user.phone?.let { ProfileRow(title = "Телефон", value = it) }
        ProfileRow(title = "Бренд", value = AppBrandCodename)
        ProfileRow(title = "Версия", value = "0.1.0")
    }
}
```

- [ ] **Step 3: `ProfileNavGraph.kt`**

```kotlin
package com.example.template.feature.profile

import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.template.core.data.MessengerRepository
import com.example.template.core.navigation.TabRoute

fun NavGraphBuilder.profileScreen(repository: MessengerRepository) {
    composable(TabRoute.Profile.route) {
        val vm = remember { ProfileViewModel(repository) }
        ProfileScreen(viewModel = vm)
    }
}
```

- [ ] **Step 4: Verify + commit**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :feature:profile:assembleDebug
git add feature/profile/
git commit -m "feat(profile): add ProfileScreen with user info"
```

---

### Task G5: `:feature:chatdetail`

**Files:**
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailViewModel.kt`
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailScreen.kt`
- Create: `feature/chatdetail/src/main/java/com/example/template/feature/chatdetail/ChatDetailNavGraph.kt`

- [ ] **Step 1: `ChatDetailViewModel.kt`**

```kotlin
package com.example.template.feature.chatdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.template.core.data.MessengerRepository
import com.example.template.core.model.Chat
import com.example.template.core.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatDetailViewModel(
    private val chatId: String,
    private val repository: MessengerRepository,
) : ViewModel() {
    val chat: Chat? = repository.getChat(chatId)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    init {
        viewModelScope.launch {
            repository.loadMessages(chatId).collect { _messages.value = it }
        }
    }

    fun sendText(body: String) {
        if (body.isBlank()) return
        viewModelScope.launch { repository.sendTextMessage(chatId, body) }
    }
}
```

- [ ] **Step 2: `ChatDetailScreen.kt`**

```kotlin
package com.example.template.feature.chatdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.components.backgroundpattern.BackgroundPatternView
import com.example.components.headers.HeadersView
import com.example.template.core.model.ChatType
import com.example.template.core.ui.hosts.HeaderHost
import com.example.template.core.ui.hosts.MessageList
import com.example.template.core.ui.hosts.MessagePanelHost

@Composable
fun ChatDetailScreen(
    viewModel: ChatDetailViewModel,
    onBack: () -> Unit,
) {
    val chat = viewModel.chat ?: return
    val messages by viewModel.messages.collectAsState()

    val subtitle = when (chat.type) {
        ChatType.P2P -> "был(а) в сети недавно"
        ChatType.Group -> "${chat.participantIds.size} участников"
        ChatType.Channel -> "канал"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderHost(
            config = HeadersView.HeaderConfig.Chat(
                name = chat.title,
                subtitle = subtitle,
                showCallButton = chat.type != ChatType.Channel,
                showSearchButton = true,
                onBack = onBack,
                onSearchClick = { /* no-op */ },
                onCallClick = { /* no-op */ },
            ),
        )
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { BackgroundPatternView(it) },
                update = { /* configure with brand + paletteIndex=0 */ },
            )
            MessageList(messages = messages, modifier = Modifier.fillMaxSize())
        }
        MessagePanelHost(onSendText = viewModel::sendText)
    }
}
```

- [ ] **Step 3: `ChatDetailNavGraph.kt`**

```kotlin
package com.example.template.feature.chatdetail

import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.template.core.data.MessengerRepository
import com.example.template.core.navigation.NavRoute

fun NavGraphBuilder.chatDetailScreen(
    repository: MessengerRepository,
    onBack: () -> Unit,
) {
    composable(NavRoute.ChatDetail.PATTERN) { entry ->
        val chatId = entry.arguments?.getString(NavRoute.ChatDetail.ARG_CHAT_ID)!!
        val vm = remember(chatId) { ChatDetailViewModel(chatId, repository) }
        ChatDetailScreen(viewModel = vm, onBack = onBack)
    }
}
```

- [ ] **Step 4: Verify + commit**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :feature:chatdetail:assembleDebug
git add feature/chatdetail/
git commit -m "feat(chatdetail): add ChatDetailScreen with message list and send panel"
```

---

## Section H — App integration

### Task H1: AppContainer + TemplateApp

**Files:**
- Modify: `app/src/main/java/com/example/template/TemplateApp.kt`
- Create: `app/src/main/java/com/example/template/AppContainer.kt`

- [ ] **Step 1: `AppContainer.kt`**

```kotlin
package com.example.template

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.template.core.data.MessengerRepository
import com.example.template.core.data.MockRepositoryImpl

class AppContainer(context: Context) {
    val repository: MessengerRepository = MockRepositoryImpl(context.applicationContext)
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
```

- [ ] **Step 2: Modify `TemplateApp.kt`**

```kotlin
package com.example.template

import android.app.Application

class TemplateApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/template/
git commit -m "feat(app): add AppContainer and wire MockRepository into Application"
```

---

### Task H2: MainActivity + NavHost + MainScaffold

**Files:**
- Modify: `app/src/main/java/com/example/template/MainActivity.kt`
- Create: `app/src/main/java/com/example/template/MainScaffold.kt`

- [ ] **Step 1: Modify `MainActivity.kt`**

```kotlin
package com.example.template

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.template.core.navigation.NavRoute
import com.example.template.core.ui.AppTheme
import com.example.template.feature.chatdetail.chatDetailScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TemplateApp).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                AppTheme {
                    val rootNav = rememberNavController()
                    NavHost(navController = rootNav, startDestination = NavRoute.Main.route) {
                        composable(NavRoute.Main.route) {
                            MainScaffold(rootNavController = rootNav)
                        }
                        chatDetailScreen(
                            repository = container.repository,
                            onBack = { rootNav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: `MainScaffold.kt`**

```kotlin
package com.example.template

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.example.template.core.navigation.TabRoute
import com.example.template.core.navigation.navigateToChatDetail
import com.example.template.core.ui.hosts.BottomTabsHost
import com.example.template.feature.calls.callsScreen
import com.example.template.feature.chats.chatsScreen
import com.example.template.feature.profile.profileScreen
import com.example.template.feature.spaces.spacesScreen

@Composable
fun MainScaffold(rootNavController: NavController) {
    val container = (LocalAppContainer.current)
    val repo = container.repository
    val tabNav = rememberNavController()
    val openChat = remember(rootNavController) { { id: String -> rootNavController.navigateToChatDetail(id) } }

    Scaffold(
        bottomBar = { BottomTabsHost(tabNavController = tabNav) },
    ) { padding ->
        NavHost(
            navController = tabNav,
            startDestination = TabRoute.Chats.route,
            modifier = Modifier.padding(padding),
        ) {
            chatsScreen(repository = repo, onChatClick = openChat)
            spacesScreen(repository = repo, onChatClick = openChat)
            callsScreen(repository = repo)
            profileScreen(repository = repo)
        }
    }
}
```

- [ ] **Step 3: Verify full build**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/template/MainActivity.kt app/src/main/java/com/example/template/MainScaffold.kt
git commit -m "feat(app): wire NavHost, MainScaffold, and all features"
```

---

### Task H3: Smoke run на устройстве/эмуляторе с foxtrot

**Files:** —

- [ ] **Step 1: Установить APK**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:installDebug`
Expected: INSTALL SUCCESSFUL.

- [ ] **Step 2: Запустить приложение, визуально проверить**

Чек-лист:
- [ ] Приложение стартует без crash
- [ ] Видна вкладка «Чаты» с заголовком «Чаты» и хотя бы 1 строкой ChatListItemView
- [ ] Переключение на «Пространства» работает, виден switcher и чаты пространства
- [ ] Переключение на «Звонки» работает, видны строки CallRow
- [ ] Переключение на «Профиль» работает, видны имя/email/phone/Бренд=foxtrot/Версия
- [ ] Тап по строке чата открывает Chat detail с фоном BackgroundPatternView и сообщениями
- [ ] В chat detail виден MessagePanelView; ввод текста и тап send добавляет сообщение в список
- [ ] Bottom bar скрыт на экране chat detail
- [ ] Back из chat detail возвращает на вкладку, откуда зашли

**Если что-то падает или не отображается:** ищем по logcat (`adb logcat -e "com.example.template" -t 200`), фиксируем найденные баги до перехода к Task H4.

---

### Task H4: Smoke build всех 5 брендов

**Files:** —

- [ ] **Step 1: Перебор брендов**

Run для каждого:
```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug -Pbrand=foxtrot
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug -Pbrand=tango
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug -Pbrand=sierra
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug -Pbrand=kilo
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug -Pbrand=love
```

Expected: каждый — BUILD SUCCESSFUL.

- [ ] **Step 2: Verify запрет неизвестного бренда**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug -Pbrand=unknown`
Expected: BUILD FAIL с сообщением `Unknown brand: unknown`.

- [ ] **Step 3: Установить хотя бы один не-foxtrot и проверить визуал**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:installDebug -Pbrand=tango`

Чек-лист:
- [ ] В профиле строка «Бренд» = `tango`
- [ ] Акцентные цвета (счётчики непрочитанных, badge'ы) отличаются от foxtrot — синие
- [ ] BackgroundPatternView в chat detail отображает Tango-палитру

- [ ] **Step 4: Verify исходники не содержат внутренних enum-имён `DSBrand`**

Запустить grep по всем enum-константам из `../android-components/components/.../DSBrand.kt` (имена видны там в `enum class DSBrand(...)`) в каталогах `app/`, `core/`, `feature/`, `build-logic/` android-template (только `*.kt`/`*.kts`). Ожидаемый результат: ноль совпадений.

Если найдены — рефакторить, использовать `AppBrand` через `LocalAppBrand.current` или `DSBrand.byCodename(...)`.

---

### Task H5: README

**Files:**
- Create: `README.md`

- [ ] **Step 1: `README.md`**

```markdown
# android-template

Каркас Android-мессенджера для тестирования дизайн-гипотез. Подключает компоненты из `../android-components` через Gradle composite build.

## Требования

- Android SDK (compileSdk 34, minSdk 26)
- JDK 17 (Android Studio JBR)
- Соседние локальные клоны: `../android-components`, `../icons-library`

## Сборка

Бренд задаётся параметром `-Pbrand=<codename>`. Поддерживаемые codename: `foxtrot`, `tango`, `sierra`, `kilo`, `love`. По умолчанию — `foxtrot`.

```bash
# Default (foxtrot)
./gradlew :app:assembleDebug

# Конкретный бренд
./gradlew :app:assembleDebug -Pbrand=tango

# Установка на устройство
./gradlew :app:installDebug -Pbrand=sierra
```

## Структура

См. `docs/superpowers/specs/2026-05-13-android-template-design.md` для архитектурного обзора.

## Тесты

```bash
./gradlew :core:data:testDebugUnitTest
```
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add README with build instructions and brand parameter"
```

---

## Self-Review

**Покрытие spec-секций:**
- §2 Стек → Task A1 (Version Catalog)
- §3 Модули → Tasks A2-A3 (convention plugins + module skeleton)
- §3.2 Composite build → Task A1 step 2 (settings.gradle.kts) + A4 (verify)
- §3.3 DSBrand.byCodename → Task B1
- §4 Навигация → Task E1 (routes) + H2 (NavHost + MainScaffold)
- §5 Модели данных → Tasks C1-C5
- §5.2 JSON-файлы → Task D6
- §5.3 Стратегия загрузки → Task D4 (lazy loadMessages)
- §5.4 Repository API + sendTextMessage → Tasks D3, D4, D7
- §6 Build-time бренд → Task B2 (AppBrand) + Task A3 step 10 (app build.gradle.kts) + Task H4 (verify all 5)
- §6.5 Запрет литералов → Task H4 step 4 (grep verify)
- §7.1 Карта экранов → Tasks G1-G5
- §7.3 Custom composables → Tasks F1-F8
- §8 State/DI → Task H1 (AppContainer)
- §9 Тестирование → Tasks D2, D5, D7 (core/data unit tests) + H4 (smoke 5 брендов)
- §10 Сборка → Task H5 (README)

**Placeholder scan:** В нескольких Host-компонентах оставлены "engineer note" комментарии (ChatListHost, BottomTabsHost, MessageList, MessagePanelHost) — это явные указания на консультацию с конкретной строкой исходника, не TBD. Принимается как осознанное решение: полные сигнатуры configure() в этих компонентах слишком обширны (например, ChatListItemView.configure имеет 30+ параметров), копировать целиком в план непродуктивно. Engineer открывает целевой файл и мапит поля.

**Type consistency:** Имена согласованы между задачами — `MessengerRepository`, `MockRepositoryImpl`, `AppBrand`, `LocalAppBrand`, `LocalIsDark`, `AppContainer`, `LocalAppContainer`, `TabRoute`, `NavRoute`, `chatsScreen`/`spacesScreen`/`callsScreen`/`profileScreen`/`chatDetailScreen` (extension functions на NavGraphBuilder). Подписи `Chat`, `Message`, `Call` совпадают между `:core:model` и использованием в фичах.

**Открытые места** (известны заранее, документированы в spec §12):
- Точные параметры `HeaderConfig.Main` / `Custom` для всех экранов (особенно для Calls/Profile — используется `Custom` вместо несуществующих режимов CALLS/PROFILE)
- API `<X>ColorScheme.from(brand, isDark)` vs `brand.<x>ColorScheme(isDark)` — уточняется чтением `DSBrand.kt`. В плане используется паттерн `brand.<x>ColorScheme(isDark)` (instance method enum), но если фактически статика — поправить
- Имена иконок call-incoming/outgoing/missed/video в icons-library — выбрать при имплементации Task F7
