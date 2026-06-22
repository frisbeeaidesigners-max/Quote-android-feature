plugins {
    id("android.library.convention")
}

val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

android {
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.findVersion("compose-compiler").get().toString()
    }
}

dependencies {
    add("implementation", platform(libs.findLibrary("compose-bom").get()))
    add("implementation", libs.findLibrary("compose-ui").get())
    add("implementation", libs.findLibrary("compose-foundation").get())
    add("implementation", libs.findLibrary("compose-material3").get())
    // Material1 — нужен только из-за rememberRipple(color=) для colored ripple'ов в Compose.
    // Foundation такого API не даёт, material3-ripple не позволяет тинтить произвольным цветом.
    // Использование точечное: списочные элементы в Profile (basicColor55 ripple = same as
    // secondary ButtonView). Сам rememberRipple импортируется из androidx.compose.material.ripple.
    add("implementation", libs.findLibrary("compose-material").get())
    add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
    add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
    add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
    add("implementation", libs.findLibrary("androidx-navigation-compose").get())
    add("implementation", libs.findLibrary("kotlinx-coroutines-android").get())
    add("implementation", libs.findLibrary("components").get())
}
