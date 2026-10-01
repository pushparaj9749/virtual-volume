import java.util.Properties
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Release signing is resolved from, in order:
 *  1. `keystore.properties` at the root of the Android project (git-ignored, used for local builds)
 *  2. environment variables, which is how the GitHub Actions release workflow supplies the
 *     `ANDROID_KEYSTORE_*` repository secrets without ever writing them to a tracked file.
 *
 * Nothing here ever prints a secret value.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun signingProperty(propertyKey: String, envKey: String): String? =
    (keystoreProperties.getProperty(propertyKey) ?: System.getenv(envKey))
        ?.takeIf { it.isNotEmpty() }

val releaseStoreFilePath = signingProperty("storeFile", "ANDROID_KEYSTORE_FILE")
val releaseStoreFile = releaseStoreFilePath?.let { rootProject.file(it) }?.takeIf { it.exists() }
val releaseStorePassword = signingProperty("storePassword", "ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingProperty("keyAlias", "ANDROID_KEY_ALIAS")
val releaseKeyPassword = signingProperty("keyPassword", "ANDROID_KEY_PASSWORD")

val appVersion = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val appVersionCode = appVersion.getProperty("versionCode").toInt()
val appVersionName = appVersion.getProperty("versionName")
require(appVersionCode > 0 && Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?").matches(appVersionName)) { "Invalid semantic version in version.properties" }
val allowUnsignedRelease = providers.gradleProperty("allowUnsignedRelease").orNull == "true"

val hasReleaseSigning = releaseStoreFile != null &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "dev.virtualvolume.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.virtualvolume.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
            )
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        sarifReport = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

// Print full assertion details for failing unit tests. Gradle's default console output
// only shows "java.lang.AssertionError at File.kt:103", which says where a test died but
// not what it expected — that detail is what makes a CI failure actionable.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

tasks.register("printReleaseSigningStatus") {
    description = "Reports whether a release signing configuration was found (never prints secret values)."
    doLast {
        println("Release signing configured: $hasReleaseSigning")
        println("Keystore file present: ${releaseStoreFile != null}")
        println("Key alias present: ${!releaseKeyAlias.isNullOrBlank()}")
    }
}

// Fail closed: CI may explicitly compile an unsigned release, but it can never publish it.
val validateReleaseSigning by tasks.registering {
    doLast {
        check(hasReleaseSigning || allowUnsignedRelease) {
            "Release signing is required. Provide the documented signing environment, or use -PallowUnsignedRelease=true for verification only."
        }
        if (hasReleaseSigning) check(releaseKeyAlias == "virtual-volume") { "The release key alias must be virtual-volume." }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(validateReleaseSigning) }
