plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.vibeshell"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.vibeshell"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = false
        buildConfig = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
