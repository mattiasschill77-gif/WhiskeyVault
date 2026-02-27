import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.schill.whiskeyvault"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.schill.whiskeyvault"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // --- SÄKER NYCKELHANTERING FÖR GITHUB ---
        val properties = Properties()
        val propertiesFile = project.rootProject.file("local.properties")
        if (propertiesFile.exists()) {
            properties.load(propertiesFile.inputStream())
        }

        buildConfigField(
            "String",
            "GEMINI_KEY",
            "\"${properties.getProperty("GEMINI_API_KEY") ?: ""}\""
        )
    }

    // --- 16 KB PAGE ALIGNMENT (ANDROID 16 READY) ---
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Alternativt sätt att sätta 16kb-flaggan om DSL:en bråkar
            extra["experimental.properties"] = mapOf("android.bundle.pageSize" to 16384)
        }
        getByName("debug") {
            extra["experimental.properties"] = mapOf("android.bundle.pageSize" to 16384)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // Matchar din Kotlin-version 1.9.22
        kotlinCompilerExtensionVersion = "1.5.10"
    }
}

dependencies {
    // Vi använder alias(libs...) för att hämta versionerna från din libs.versions.toml
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Lifecycle för Compose (viktigt för UI-stabilitet)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // --- KAMERA & SCANNING ---
    val cameraVersion = "1.3.1"
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // --- GEOLOCATION ---
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // --- COMPOSE UI & MATERIAL ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // --- GOOGLE AI / GEMINI ---
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // --- BILDHANTERING & NÄTVERK ---
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // --- DATABAS (ROOM) ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- TEST ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}