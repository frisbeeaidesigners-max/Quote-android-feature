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
        buildConfigField("String", "APP_VERSION", "\"0.1.0\"")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:navigation"))
    // Material — нужен Theme.Material3.DayNight.NoActionBar для ContextThemeWrapper'а вокруг
    // ReactionsView в MessageList (textColorPrimary 100% alpha, иначе эмодзи тусклые на 87%
    // alpha системной Material темы Theme.Template — см. project-context-menu-integration).
    implementation("com.google.android.material:material:1.11.0")
    testImplementation(libs.junit)
    testImplementation(project(":core:model"))
}
