plugins {
    alias(libs.plugins.android.application)
    // No org.jetbrains.kotlin.android plugin - AGP 9+ has built-in Kotlin support.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.obliprabu.plansaver"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.obliprabu.plansaver"
        minSdk = 24
        targetSdk = 37
        versionCode = 5
        versionName = "0.2.3"
        // Hosted deals.json (refreshed by .github/workflows/deals-robot.yml). Needs the repo PUBLIC; if it is private the app quietly falls back to bundled deals.
        buildConfigField("String", "DEALS_URL", "\"https://raw.githubusercontent.com/ObliPrabu/PlanSaver/main/deals-data/deals.json\"")
        // Hosted catalog.json (phones, cars, insurers; refreshed by .github/workflows/catalog-robot.yml); the app also bundles a copy.
        buildConfigField("String", "CATALOG_URL", "\"https://raw.githubusercontent.com/ObliPrabu/PlanSaver/main/deals-data/catalog.json\"")
    }

    // One catalog.json for the robot, the repo and the app: bundle the deals-data folder as assets (offline fallback).
    sourceSets { getByName("main") { assets.srcDir("../deals-data") } }

    buildFeatures {
        compose = true
        buildConfig = true // BuildConfig.VERSION_NAME for the on-screen version label
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013") // Android's org.json is a stub in plain JVM unit tests
}
