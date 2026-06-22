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
