pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
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

rootProject.name = "WSChat"

include(":app")
include(":core:common")
include(":core:network")
include(":core:security")

// Feature: Chat
include(":feature:chat:domain")
include(":feature:chat:data")
include(":feature:chat:presentation")

// Feature: Stock
include(":feature:stock:domain")
include(":feature:stock:data")
include(":feature:stock:presentation")

// Feature: Auth
include(":feature:auth:domain")
include(":feature:auth:data")
include(":feature:auth:presentation")

// Feature: Order (buy/sell)
include(":feature:order:domain")
include(":feature:order:data")
include(":feature:order:presentation")
