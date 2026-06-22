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
