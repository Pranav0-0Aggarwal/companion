import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val local = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "app.companion"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.companion"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("String", "GMAIL_CLIENT_ID", "\"${local.getProperty("gmail.webClientId", "")}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = local.getProperty("release.store")?.let(::file)
            storePassword = local.getProperty("release.password")
            keyAlias = "companion"
            keyPassword = local.getProperty("release.password")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        resources.excludes += listOf("META-INF/**/LICENSE*", "META-INF/*.version", "META-INF/*.kotlin_module", "kotlin/**", "DebugProbesKt.bin")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.fragment)
    implementation(libs.lifecycle.runtime)
    implementation(libs.navigation)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.work)
    implementation(libs.glance)
    implementation(libs.biometric)
    implementation(libs.window)
    implementation(libs.coroutines)
    implementation(libs.play.auth)
    implementation(libs.litert)
}
