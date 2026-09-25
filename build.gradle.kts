// Top-level build file. Plugins are declared here (not applied) so every module
// resolves the same versions from the version catalog.
plugins {
    alias(libs.plugins.android.application) apply false
    // Pins the Kotlin Gradle Plugin used by AGP's built-in Kotlin support.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
