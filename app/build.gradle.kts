plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.inayatechlab.filemanagerpro"
    compileSdk = 34

    // Release signing is injected from environment variables (GitHub Secrets on CI).
    // Keystore material itself is NEVER stored in the repository.
    // (env-* prefixes avoid name clashes with the SigningConfig DSL properties)
    val envStoreFile = System.getenv("KEYSTORE_FILE")
    val envStorePass = System.getenv("KEYSTORE_PASSWORD")
    val envKeyAlias = System.getenv("KEY_ALIAS")
    val envKeyPass = System.getenv("KEY_PASSWORD")
    val hasReleaseSigning = !envStoreFile.isNullOrBlank() &&
        !envStorePass.isNullOrBlank() &&
        !envKeyAlias.isNullOrBlank()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(envStoreFile!!)
                storePassword = envStorePass
                keyAlias = envKeyAlias
                keyPassword = envKeyPass ?: envStorePass
            }
        }
    }

    defaultConfig {
        applicationId = "com.inayatechlab.filemanagerpro"
        minSdk = 26
        targetSdk = 34
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    testOptions {
        unitTests.all {
            it.maxHeapSize = "512m"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    implementation("com.google.android.material:material:1.12.0")

    // Vault biometric unlock (BiometricPrompt)
    implementation("androidx.biometric:biometric:1.1.0")

    // Image loading for local files & previews
    implementation("io.coil-kt:coil:2.6.0")

    // Archive extraction: .tar/.tar.gz/.tgz via commons-compress, .7z (LZMA/xz),
    // .rar (RAR4) via junrar. slf4j-nop silences junrar's logger on Android.
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.9")
    implementation("com.github.junrar:junrar:7.5.5")
    implementation("org.slf4j:slf4j-nop:1.7.36")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Local (JVM) unit tests
    testImplementation("junit:junit:4.13.2")

    // Instrumented UI smoke tests (Espresso)
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
