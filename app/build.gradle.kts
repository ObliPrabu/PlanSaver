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
        versionCode = 1
        versionName = "0.1.0"
    }

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
}
