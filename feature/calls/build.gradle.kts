plugins {
    id("android.feature.convention")
}

android {
    namespace = "com.example.template.feature.calls"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
}
