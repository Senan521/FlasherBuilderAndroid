plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "app.flasherbuilder"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.flasherbuilder"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }   // yerel araçlar yalnızca arm64
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true               // lib*.so dosyaları diske çıkarılır (çalıştırılabilmesi için)
            keepDebugSymbols += "**/*.so"           // hazır, zaten strip edilmiş binary'lere dokunma
        }
    }

    val ksPath = System.getenv("KEYSTORE_PATH")
    signingConfigs {
        if (ksPath != null) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Keystore secret yoksa debug anahtarıyla imzalanır (kurulabilir APK).
            signingConfig = if (ksPath != null) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }

// Backend (Flasher_Builder'ın Python kodu) uygulama içinde çalışır. buildPython sürümü 3.12 ile aynı olmalı.
chaquopy {
    defaultConfig {
        version = "3.12"
    }
}

dependencies {
    implementation("androidx.compose.material3:material3:1.4.0-alpha15")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
