plugins {
    `kotlin-dsl`
}

val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

dependencies {
    implementation("com.android.tools.build:gradle:${libs.findVersion("agp").get()}")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.findVersion("kotlin").get()}")
}
