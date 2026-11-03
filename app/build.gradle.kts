// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

import java.util.Base64
import java.util.Properties
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.auxo.pos"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.auxo.pos"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "1.0.0"

        // The final build checks GitHub for updates and keeps backups in Documents/Auxo. The debug build overrides both.
        buildConfigField("boolean", "UPDATES_ENABLED", "true")
        buildConfigField("String", "BACKUP_FOLDER", "\"Auxo\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // One shared key for debug builds so a new debug APK installs over an older one (CI used to make a fresh key each run).
    val debugSigning = layout.projectDirectory.dir("debug-signing").asFile
    val debugKeystore = layout.buildDirectory.get().asFile.resolve("debug-signing/debug.p12")
    debugKeystore.parentFile.mkdirs()
    debugKeystore.writeBytes(
        Base64.getMimeDecoder().decode(debugSigning.resolve("debug-keystore.b64").readText()),
    )
    val debugProps = Properties().apply { debugSigning.resolve("debug-signing.properties").inputStream().use { load(it) } }

    signingConfigs {
        getByName("debug") {
            storeFile = debugKeystore
            storePassword = debugProps.getProperty("storePassword")
            keyAlias = debugProps.getProperty("keyAlias")
            keyPassword = debugProps.getProperty("keyPassword")
        }
        // Release signing comes only from the environment (the release workflow sets these). Without them the
        // release build stays unsigned, so nothing secret is ever stored in the repository.
        val releaseKeystore = System.getenv("AUXO_KEYSTORE_FILE")
        if (!releaseKeystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("AUXO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AUXO_KEY_ALIAS")
                keyPassword = System.getenv("AUXO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Off by default (it slows the tests). CI's coverage job turns it on with -Pcoverage.
            enableUnitTestCoverage = providers.gradleProperty("coverage").isPresent

            // A debug build installs beside the final build instead of clashing with it: its own app ID (so the two
            // have separate data and different signing keys do not matter), its own name (see src/debug/res), no
            // update check (updates are for the final build only) and its own backup folder.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "UPDATES_ENABLED", "false")
            buildConfigField("String", "BACKUP_FOLDER", "\"Auxo Debug\"")
        }
        release {
            // R8 shrinks and obfuscates the code and removes unused resources, which makes the app much smaller.
            // Keep rules for libraries that look classes up by name are in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    bundle {
        // The language can be changed inside the app, so every language must ship in the base install.
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Robolectric needs merged resources and the manifest for Room and Compose UI tests.
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.withType<Test>().configureEach {
    // Put the assertion message of a failed test in the CI log, not only its line number.
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
        showCauses = true
    }
    // Robolectric and Compose UI tests need more than the default 512 MB.
    maxHeapSize = "2g"
    // Robolectric's Android 16+ sandbox sets FileDescriptor internals through
    // jdk.internal.access.SharedSecrets, which JDK 17+ hides unless exported.
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
    )
    // Robolectric loads app classes through its own class loader; count those too.
    extensions.findByType(JacocoTaskExtension::class.java)?.apply {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

ksp {
    // Room writes its schema here so future database migrations can be checked against it.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.navigation.suite)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    // Fingerprint check for the lock screen. BiometricPrompt needs a FragmentActivity, which this brings in.
    implementation(libs.androidx.biometric)
    // Newer than the one biometric pulls in; fixes permission requests from Compose in a FragmentActivity.
    implementation(libs.androidx.fragment)
    // Reads the camera orientation of product photos (replaces android.media.ExifInterface).
    implementation(libs.androidx.exifinterface)

    implementation(libs.tink.android)
    // Encrypts the database file on the phone; its key is kept in the Android Keystore.
    implementation(libs.sqlcipher.android)
    implementation(libs.zxing.core)
    implementation(libs.play.services.code.scanner)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // Device tests (src/androidTest), run on an emulator in CI.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
