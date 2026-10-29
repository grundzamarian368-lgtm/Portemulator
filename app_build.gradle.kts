plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "cz.padstudio"
    compileSdk = 34
    defaultConfig {
        applicationId = "cz.padstudio"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "0.2"
        ndk { abiFilters.add("arm64-v8a") }
    }
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.activity:activity:1.8.2")
    implementation("com.github.Swordfish90:libretrodroid:0.14.0")
}
