import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 서명 키는 GitHub Secrets → 환경변수로만 받습니다 (코드에 비밀번호를 넣지 않음)
val keystorePath: String? = System.getenv("KBI_KEYSTORE_PATH")
val hasReleaseKey = keystorePath != null && File(keystorePath).exists()

android {
    namespace = "com.kbi.appcleaner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kbi.appcleaner"
        minSdk = 26          // 안드로이드 8.0 이상 (게임 카테고리 정보가 이 버전부터 제공)
        targetSdk = 36       // 2026년 플레이스토어 신규 앱 요구 수준
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("KBI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KBI_KEY_ALIAS")
                keyPassword = System.getenv("KBI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.12.0")
}
