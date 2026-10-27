plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.meta.spatial.plugin")
}

android {
    namespace = "com.farrah.sproutvision"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.farrah.sproutvision.quest"
        // Quest 3 runs Horizon OS on Android 12L (API 32) — minSdk must stay <= 32.
        minSdk = 32
        targetSdk = 34
        versionCode = 2
        versionName = "1.0-quest"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Keep the plugin and every Spatial SDK module on the same version.
// Latest: https://central.sonatype.com/artifact/com.meta.spatial/meta-spatial-sdk
val spatialSdkVersion = "0.10.1"

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Meta Spatial SDK — native Quest 3 passthrough + spatial panels.
    implementation("com.meta.spatial:meta-spatial-sdk:$spatialSdkVersion")
    implementation("com.meta.spatial:meta-spatial-sdk-toolkit:$spatialSdkVersion")
    implementation("com.meta.spatial:meta-spatial-sdk-vr:$spatialSdkVersion")
    implementation("com.meta.spatial:meta-spatial-sdk-compose:$spatialSdkVersion")
    implementation("com.meta.spatial:meta-spatial-sdk-isdk:$spatialSdkVersion")
    ksp("com.meta.spatial.plugin:com.meta.spatial.plugin.gradle.plugin:$spatialSdkVersion")

    // Room (tray + stage history storage)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore (settings: API key, demo mode) + WorkManager (reminders, best-effort on Quest)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // OkHttp for Gemini Developer API calls (plant analysis)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Local unit tests (pure-JVM seed/photo-name checks)
    testImplementation("junit:junit:4.13.2")

    // NOTE: no CameraX — Quest 3's passthrough cameras are driven directly
    // via the Camera2 API (see ui/QuestScanScreen.kt).
}
