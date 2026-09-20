plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.omnitrix.watchface"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.omnitrix.watchface"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-spike"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // Two per-watch build variants. samsung links the Samsung Health Sensor
    // SDK (local .aar, needed for its Health Tracking Service); generic
    // excludes it and relies on Health Services + SensorManager only. Same
    // code otherwise -- the variant-specific SamsungHealthStats object is
    // provided per source set (src/samsung vs src/generic).
    flavorDimensions += "device"
    productFlavors {
        create("generic") {
            dimension = "device"
        }
        create("samsung") {
            dimension = "device"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.wear.watchface)
    implementation(libs.wear.watchface.complications.rendering)
    implementation(libs.wear.watchface.complications.data.source.ktx)
    implementation(libs.wear.watchface.editor)
    implementation(libs.wear.watchface.style)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.health.services.client)
    implementation(libs.androidx.health.connect.client)
    // String form: AGP registers flavor configurations at runtime, after the
    // Kotlin DSL compiles this script, so there's no typed accessor for it.
    add("samsungImplementation", files("libs/samsung-health-sensor-api-1.4.1.aar"))
}
