plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lonnnnnng.biucar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lonnnnnng.biucar"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // long：正式包必须使用本机受保护的发布密钥，避免把 debug 证书分发给车机用户。
    signingConfigs {
        create("release") {
            val storePath = providers.gradleProperty("biucarReleaseStoreFile").orNull
                ?: error("缺少 biucarReleaseStoreFile，请配置本机车机发布签名")
            storeFile = file(storePath)
            storePassword = providers.gradleProperty("biucarReleaseStorePassword").orNull
                ?: error("缺少 biucarReleaseStorePassword，请配置本机车机发布签名")
            keyAlias = providers.gradleProperty("biucarReleaseKeyAlias").orNull
                ?: error("缺少 biucarReleaseKeyAlias，请配置本机车机发布签名")
            keyPassword = providers.gradleProperty("biucarReleaseKeyPassword").orNull
                ?: error("缺少 biucarReleaseKeyPassword，请配置本机车机发布签名")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.10.1")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-session:1.10.1")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    ksp("androidx.room:room-compiler:2.8.4")
}
