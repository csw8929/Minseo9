plugins {
    alias(libs.plugins.android.application)
}

val gbisServiceKey = rootProject.file(".gbis-key")
    .takeIf { it.exists() }
    ?.readText()
    ?.trim()
    ?.replace("\\", "\\\\")
    ?.replace("\"", "\\\"")
    ?: ""

android {
    namespace = "com.example.minseo9"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.minseo9"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GBIS_SERVICE_KEY", "\"$gbisServiceKey\"")
    }

    buildFeatures {
        buildConfig = true
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
