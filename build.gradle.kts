// Versions live in settings.gradle.kts. Load AGP and Kotlin in the same root
// classloader so both modules share one Kotlin Gradle plugin instance.
// Applying neither Android plugin here means coreOnly still needs no Android SDK.
plugins {
    id("com.android.application") apply false
    kotlin("jvm") apply false
    kotlin("android") apply false
    kotlin("kapt") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
}
