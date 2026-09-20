plugins {
    id("com.android.application")
}

android {
    namespace = "com.altaf.ioslauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.altaf.ioslauncher"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "5.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
}
