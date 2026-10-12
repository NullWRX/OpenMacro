import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val vMajor = versionProps.getProperty("VERSION_MAJOR").toInt()
val vMinor = versionProps.getProperty("VERSION_MINOR").toInt()
val vPatch = versionProps.getProperty("VERSION_PATCH").toInt()
require(vMinor in 0..99 && vPatch in 0..99) { "MINOR and PATCH must be between 0 and 99" }
val appVersionName = "$vMajor.$vMinor.$vPatch"
val appVersionCode = vMajor * 10_000 + vMinor * 100 + vPatch

// Project home, used in the Open Food Facts User-Agent and the About screen.
// Change it here once the repository location is final.
val projectUrl = "https://github.com/NullWRX/OpenMacro"

// Release signing is optional: CI provides these environment variables when the
// signing secrets exist. Without them, release builds are unsigned (F-Droid signs its own).
val keystorePath: String? = System.getenv("OM_KEYSTORE_PATH")
val hasReleaseSigning = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

android {
    namespace = "io.github.nullwrx.openmacro"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.nullwrx.openmacro"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "PROJECT_URL", "\"$projectUrl\"")
        buildConfigField(
            "String",
            "OFF_USER_AGENT",
            "\"OpenMacro/$appVersionName (Android; +$projectUrl)\"",
        )
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("OM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("OM_KEY_ALIAS")
                keyPassword = System.getenv("OM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    flavorDimensions += "distribution"
    productFlavors {
        // Google Play / GitHub build: ML Kit on-device models for barcode and label scanning.
        create("play") {
            dimension = "distribution"
        }
        // F-Droid build: only free software. ZXing for barcodes; label OCR not yet available.
        create("fdroid") {
            dimension = "distribution"
            versionNameSuffix = "-fdroid"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Report problems without failing the build while the project is young;
        // CI uploads the report so issues stay visible.
        abortOnError = false
        checkReleaseBuilds = false
    }

    dependenciesInfo {
        // F-Droid asks apps not to embed the encrypted dependency blob Google adds.
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// The Room plugin keeps one schema file per database version and copies it safely for every
// build variant (the play and fdroid builds would otherwise overwrite each other's file).
room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    "playImplementation"(libs.mlkit.barcode.scanning)
    "playImplementation"(libs.mlkit.text.recognition)
    "fdroidImplementation"(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
