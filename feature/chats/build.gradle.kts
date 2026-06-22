plugins {
    id("android.feature.convention")
}

android {
    namespace = "com.example.template.feature.chats"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
}
