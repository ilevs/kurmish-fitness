import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kurmish.fitness"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kurmish.fitness"
        minSdk = 26
        targetSdk = 35
        versionCode = 31
        versionName = "0.30.0"
        // Распознавание речи GigaAM тянут только 64-битные телефоны, поэтому нативные библиотеки только arm64.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    lint {
        // Строгий Android Lint: все проверки включены, любое предупреждение валит сборку.
        checkAllWarnings = true
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        checkTestSources = true
        // Проверки «вышла новая версия» зависят от даты, а не от кода: обновления идут отдельными PR.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
        htmlReport = true
        sarifReport = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    // Бэкап Дневника на Яндекс Диск: WebDAV (нужен метод MKCOL) и фоновая отправка при появлении сети.
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    // Офлайн-распознавание речи sherpa-onnx: aar скачивает scripts/fetch-sherpa-onnx.sh.
    implementation(files("libs/sherpa-onnx.aar"))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
