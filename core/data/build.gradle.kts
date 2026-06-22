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
