// Top-level build file. Module configuration lives in app/build.gradle.kts.

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the
        // org.jetbrains.kotlin.android plugin is not applied anywhere.
        // AGP pulls in a Kotlin Gradle Plugin at runtime; pinning it here keeps
        // the Kotlin compiler on the same version as the Compose compiler plugin.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
