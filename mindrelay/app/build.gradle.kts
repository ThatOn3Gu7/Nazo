plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sahil.mindrelay"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sahil.mindrelay"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }

    val releaseKeystore = file("mindrelay-release.jks")
    signingConfigs {
        create("release") {
            if (releaseKeystore.exists()) {
                storeFile = releaseKeystore
                storePassword = providers.gradleProperty("releaseStorePassword").orElse("mindrelay-local-store").get()
                keyAlias = providers.gradleProperty("releaseKeyAlias").orElse("mindrelay").get()
                keyPassword = providers.gradleProperty("releaseKeyPassword").orElse("mindrelay-local-key").get()
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseKeystore.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    val composeUi = "1.12.1"
    val material3 = "1.5.0-alpha28"
    val activity = "1.13.0"
    implementation("androidx.activity:activity-compose:$activity")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.compose.ui:ui:$composeUi")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeUi")
    implementation("androidx.compose.foundation:foundation:$composeUi")
    implementation("androidx.compose.animation:animation:$composeUi")
    implementation("androidx.compose.material3:material3:$material3")
    implementation("androidx.compose.material:material-icons-extended:$composeUi")
    debugImplementation("androidx.compose.ui:ui-tooling:$composeUi")
}
