import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release key: keystore.properties next to the project (never in git). Without it only debug builds.
val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.kirdev.pulsedeckpanel"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kirdev.pulsedeckpanel"
        minSdk = 30
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.2"
    }

    signingConfigs {
        if (signing.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true               // R8: smaller APK, unused code removed (library rules are bundled)
            }
            proguardFiles("proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.okhttp)
    implementation(libs.play.services.code.scanner)     // QR pairing; scanning UI lives in Play services

    testImplementation(libs.junit)
    testImplementation(libs.org.json)          // org.json is only a stub in local unit tests
    testImplementation(libs.kotlinx.coroutines.test)
}
