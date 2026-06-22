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
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // Sign release with the debug keystore so it installs locally without a keystore setup.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

val syncIconsFromLibrary = tasks.register<Copy>("syncIconsFromLibrary") {
    description = "Copies SVG icons from ../../icons-library/icons into app assets so DSIcon can resolve them"
    from(file("${rootDir}/../icons-library/icons")) {
        include("*.svg")
    }
    into(layout.projectDirectory.dir("src/main/assets/icons"))
}

// Локальные SVG'шки, которых ещё нет в icons-library. Копируются ПОСЛЕ syncIconsFromLibrary
// (см. preBuild dependsOn ниже — порядок в dependsOn определяет порядок выполнения для
// `Copy` task'ов в одну директорию: последний выигрывает при совпадении имён). Файлы здесь
// git-tracked — это и есть их роль. Когда иконка попадёт в icons-library, удалить
// локальную копию из `app/src/main/assets-local/icons/` — следующая сборка возьмёт версию
// из библиотеки. Подробности в `app/src/main/assets-local/icons/README.md`.
val syncIconsLocal = tasks.register<Copy>("syncIconsLocal") {
    description = "Copies local SVG icons (not yet in icons-library) into app assets"
    from(layout.projectDirectory.dir("src/main/assets-local/icons")) {
        include("*.svg")
    }
    into(layout.projectDirectory.dir("src/main/assets/icons"))
    mustRunAfter(syncIconsFromLibrary)
}

val syncImagesFromComponents = tasks.register<Copy>("syncImagesFromComponents") {
    description = "Copies image assets from ../../android-components/app/src/main/assets/images into app assets (keeps canonical names from components)"
    from(file("${rootDir}/../android-components/app/src/main/assets/images"))
    into(layout.projectDirectory.dir("src/main/assets/images"))
    include("*.png", "*.jpg", "*.jpeg")
}

val syncPatternsFromComponents = tasks.register<Copy>("syncPatternsFromComponents") {
    description = "Copies SVG pattern assets from ../../android-components/app/src/main/assets/patterns into app assets (BackgroundPatternView loads from assets/patterns)"
    from(file("${rootDir}/../android-components/app/src/main/assets/patterns")) {
        include("*.svg")
    }
    into(layout.projectDirectory.dir("src/main/assets/patterns"))
}

val syncStickersFromComponents = tasks.register<Copy>("syncStickersFromComponents") {
    description = "Copies sticker PNG packs from ../../android-components/app/src/main/assets/stickers into app assets (MessagePanel sticker picker)"
    from(file("${rootDir}/../android-components/app/src/main/assets/stickers"))
    into(layout.projectDirectory.dir("src/main/assets/stickers"))
    include("**/*.png")
}

val syncGifsFromComponents = tasks.register<Copy>("syncGifsFromComponents") {
    description = "Copies animated WebP/GIFs from ../../android-components/app/src/main/assets/gifs into app assets (MessagePanel GIF picker)"
    from(file("${rootDir}/../android-components/app/src/main/assets/gifs"))
    into(layout.projectDirectory.dir("src/main/assets/gifs"))
    include("*.webp", "*.gif")
}

tasks.named("preBuild") {
    dependsOn(
        syncIconsFromLibrary,
        syncIconsLocal,
        syncImagesFromComponents,
        syncPatternsFromComponents,
        syncStickersFromComponents,
        syncGifsFromComponents,
    )
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
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
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
