plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "in.lbscek.attendance"
    compileSdk = 34

    defaultConfig {
        applicationId = "in.lbscek.attendance"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.14.0-alpha03")
    implementation("androidx.compose.ui:ui:1.13.0-alpha03")
    implementation("androidx.compose.material3:material3:1.5.0-alpha29")
    implementation("androidx.compose.ui:ui-tooling-preview:1.13.0-alpha03")

    // Home screen widget (Jetpack Compose for widgets)
    implementation("androidx.glance:glance-appwidget:1.3.0-alpha02")
    implementation("androidx.glance:glance-material3:1.3.0-alpha02")

    // Background refresh
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    // Encrypted local storage for Etlab credentials
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Networking + HTML scraping
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.jsoup:jsoup:1.23.2")

    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")
}
configurations.all {
    resolutionStrategy {
        force(
            "androidx.core:core:1.13.1",
            "androidx.core:core-ktx:1.13.1",
            "androidx.activity:activity:1.9.2",
            "androidx.activity:activity-ktx:1.9.2",
            "androidx.activity:activity-compose:1.9.2",
            "androidx.work:work-runtime:2.9.1",
            "androidx.work:work-runtime-ktx:2.9.1",
            "androidx.glance:glance:1.1.1",
            "androidx.glance:glance-appwidget:1.1.1",
            "androidx.glance:glance-material3:1.1.1",
            "androidx.compose.ui:ui:1.6.8",
            "androidx.compose.ui:ui-graphics:1.6.8",
            "androidx.compose.ui:ui-text:1.6.8",
            "androidx.compose.ui:ui-tooling:1.6.8",
            "androidx.compose.ui:ui-tooling-preview:1.6.8",
            "androidx.compose.ui:ui-tooling-data:1.6.8",
            "androidx.compose.foundation:foundation:1.6.8",
            "androidx.compose.foundation:foundation-layout:1.6.8",
            "androidx.compose.animation:animation:1.6.8",
            "androidx.compose.animation:animation-core:1.6.8",
            "androidx.compose.runtime:runtime:1.6.8",
            "androidx.compose.runtime:runtime-saveable:1.6.8",
            "androidx.compose.material3:material3:1.2.1",
            "androidx.compose.material:material-ripple:1.6.8",
            "com.squareup.okhttp3:okhttp:4.12.0"
        )
    }
}