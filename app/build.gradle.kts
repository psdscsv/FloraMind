import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
}

android {
    namespace = "com.psd.floramind"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.psd.floramind"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProperties = org.jetbrains.kotlin.konan.properties.Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            FileInputStream(localFile).use { localProperties.load(it) }
        }
        val apiKey = localProperties.getProperty("api_key", "")
        buildConfigField("String", "API_KEY", "\"$apiKey\"")
    }
    // 签名配置
    signingConfigs {
        create("release") {
            storeFile = file("C:\\Users\\tanru\\projects\\android\\mykey\\test3")   // 例如: "D:/keys/my-release-key.jks"
            storePassword = "123456"
            keyAlias = "key0"
            keyPassword = "123456"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    // ========== Core ==========
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.0")

    // ========== Compose ==========
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ========== Navigation ==========
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ========== ViewModel ==========
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // ========== Room ==========
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")

    // ========== DataStore ==========
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ========== Network ==========
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ========== Coroutines ==========
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ========== Gson ==========
    implementation("com.google.code.gson:gson:2.11.0")

    // ========== Coil ==========
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ========== Testing ==========
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}