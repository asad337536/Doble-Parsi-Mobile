import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Release signing.
 *
 * Order of preference:
 *   1. keystore.properties in the project root (local builds, git-ignored)
 *   2. environment variables / CI secrets
 *   3. no keystore at all -> the debug key, so a plain clone still builds
 *
 * See README ("امضای ثابت" / "Stable signing key") for how to generate the keystore.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}

fun signingValue(propertyKey: String, envKey: String): String? =
    (keystoreProperties.getProperty(propertyKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

val keystorePath = signingValue("storeFile", "KEYSTORE_FILE") ?: "release.keystore"
val keystoreFile = rootProject.file(keystorePath)
val hasReleaseKeystore = keystoreFile.exists()

val appVersionName = "1.0.0"
val appVersionCode = 10000

android {
    namespace = "com.qwaicode.dobleparsi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.qwaicode.dobleparsi"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables.useSupportLibrary = true
    }

    /*
     * Why there is only ONE apk now.
     *
     * The three release APKs (arm64-v8a / armeabi-v7a / universal) all weighed
     * exactly 41.8 MB because this app ships zero native libraries: no .so files at
     * all (pure Kotlin + Compose + OkHttp, AAC comes from the platform encoder).
     * ABI splits only strip jniLibs/<abi>, so with nothing to strip every split was
     * a byte-for-byte copy of the universal APK - three identical downloads and a
     * misleading "pick your architecture" table.
     *
     * The honest fix: build one universal APK that runs on every ABI, and cut its
     * real weight with R8 + resource shrinking instead of fake splits.
     */

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = keystoreFile
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // The actual size fix: R8 + resource shrinking on a build that used to
            // ship every unused Compose/material icon class untouched.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
