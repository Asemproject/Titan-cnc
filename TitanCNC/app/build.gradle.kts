plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("kotlin-parcelize")
}

android {
    namespace = "com.titancnc"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.titancnc"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
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
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            merges += "META-INF/LICENSE.md"
            merges += "META-INF/LICENSE-notice.md"
        }
        jniLibs {
            pickFirsts += listOf(
                "lib/arm64-v8a/libopencv_java4.so",
                "lib/armeabi-v7a/libopencv_java4.so",
                "lib/x86/libopencv_java4.so",
                "lib/x86_64/libopencv_java4.so"
            )
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    
    // ViewModel & Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    
    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)
    
    // USB Serial Communication
    implementation(libs.usb.serial.for.android)
    
    // Bluetooth
    implementation(libs.androidx.bluetooth)
    
    // OpenCV for Image Processing
    implementation(libs.opencv.android)
    
    // Networking (WebSocket/Telnet for FluidNC/grblHAL)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    
    // JSON Parsing
    implementation(libs.gson)
    implementation(libs.kotlinx.serialization.json)
    
    // 3D Graphics - OpenGL ES Helpers
    implementation(libs.rajawali)
    
    // Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    
    // Charts & Graphs
    implementation(libs.vico.compose)
    implementation(libs.vico.core)
    
    // Math & Geometry
    implementation(libs.apache.commons.math)
    
    // File Picker
    implementation(libs.androidx.documentfile)
    
    // Permissions
    implementation(libs.accompanist.permissions)
    
    // System UI Controller
    implementation(libs.accompanist.systemuicontroller)
    
    // Animation
    implementation(libs.androidx.animation.core)
    implementation(libs.androidx.animation.graphics)
    
    // Haptic Feedback
    implementation(libs.androidx.haptic.feedback)
    
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
