import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("kotlin-parcelize")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

val apiSecret: String = localProperties.getProperty("API_SECRET") ?: ""

android {
    namespace = "com.example.employeepos2"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.integritydoors.pos"
        minSdk = 26
        targetSdk = 36

        // 🔥 Bumped to 28 so Google Play accepts the clean upload!
        versionCode = 34
        versionName = "1.0"

        buildConfigField("String", "API_SECRET", "\"$apiSecret\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // Leaves native C++ libraries uncompressed for 16 KB alignment
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // 1. Core Android & UI
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // 2. Activity & Fragment
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // 3. Networking & JSON
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 4. Stripe Terminal (🔥 Using 3.10.2: Fixes libsecurepin_.so WITHOUT breaking UI)
    implementation("com.stripe:stripeterminal-localmobile:3.10.2")
    implementation("com.stripe:stripeterminal-core:3.10.2")

    // 🔥 SURGICAL FIX for libtoolChecker.so (Updates RootBeer securely without touching Stripe's UI)
    implementation("com.scottyab:rootbeer-lib:0.1.2")

    // 5. Biometrics
    implementation("androidx.biometric:biometric:1.1.0")

    // 6. Glide: Image Loading
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // 7. CameraX (🔥 1.4.0 fixes libimage_processing_util_jni.so)
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // 8. ML Kit Barcode Scanning (🔥 17.3.0 fixes libbarhopper_v3.so)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Standard Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Firebase & WorkManager
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    // Security Crypto
    implementation("androidx.security:security-crypto:1.1.0")

    // Gson (For Draft Saving)
    implementation("com.google.code.gson:gson:2.10.1")
}

// FORCE RESOLUTION
configurations.all {
    resolutionStrategy {
        // 🔥 Left exactly as you wanted it to protect your UI buttons!
        force("androidx.core:core-ktx:1.12.0")
        force("androidx.appcompat:appcompat:1.6.1")
        force("com.google.android.material:material:1.11.0")

        // 🔥 Forces ML Kit to stay at 17.3.0 so it doesn't get downgraded secretly
        force("com.google.mlkit:barcode-scanning:17.3.0")
    }
}