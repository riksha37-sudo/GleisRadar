pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.3.21"
        id("com.android.application") version "8.13.2"
        id("org.jetbrains.kotlin.android") version "2.3.21"
        id("org.jetbrains.kotlin.kapt") version "2.3.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    }
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
rootProject.name = "BerlinStoerungswaechter"
include(":core")
// Core tests can run on a workstation without the Android SDK.
if (providers.gradleProperty("coreOnly").orNull != "true") include(":app")
