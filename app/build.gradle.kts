plugins {
    id("com.android.application")
}

android {
    namespace = "top.ss2a.widget"
    compileSdk = 34

    defaultConfig {
        applicationId = "top.ss2a.widget"
        minSdk = 26
        targetSdk = 34
        versionCode = 28
        versionName = "1.9.6"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
}