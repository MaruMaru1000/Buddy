plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

val baseVersion = "1.5"

// Release signing. The key and its passwords live OUTSIDE the repo, in
// ~/.gradle/gradle.properties (BUDDY_RELEASE_*). Without them, release
// builds are simply unsigned.
val releaseStoreFile = findProperty("BUDDY_RELEASE_STORE_FILE") as String?

android {
    namespace = "com.buddyapp.Buddy"
    compileSdk = 35
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.buddyapp.Buddy"
        minSdk = 23
        targetSdk = 35
        versionCode = 6
        versionName = "$baseVersion"

        vectorDrawables { useSupportLibrary = true }

        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = findProperty("BUDDY_RELEASE_STORE_PASSWORD") as String?
                keyAlias = findProperty("BUDDY_RELEASE_KEY_ALIAS") as String?
                keyPassword = findProperty("BUDDY_RELEASE_KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        release {
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures { 
        compose = true 
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

chaquopy {
    defaultConfig {
        version = "3.12"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.compose.material:material-icons-extended")
}