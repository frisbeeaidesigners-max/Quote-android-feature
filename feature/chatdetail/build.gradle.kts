plugins {
    id("android.feature.convention")
}

android {
    namespace = "com.example.template.feature.chatdetail"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    // BottomSheetDialog — Action sheet для контекстного меню сообщений. Compose-овский Dialog
    // воюет с measurement'ом ContextMenuView; нативный BottomSheetDialog решает это сразу.
    implementation("com.google.android.material:material:1.11.0")
}
