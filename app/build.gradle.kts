import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Firma de release fuera del repo: keystore.properties (gitignored) define
// storeFile/storePassword/keyAlias/keyPassword. Sin el archivo, assembleRelease
// produce un AAB/APK sin firmar (útil en CI sin secrets).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "com.paylensu"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.paylensu"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("es", "en")
        // Arranque en dispositivos de 1 GB: solo es/en, menos recursos en la APK.
    }

    // OCR bundled (modelo Latin ~6MB dentro del APK, no requiere GMS) o thin (Play Services).
    flavorDimensions += "ocr"
    productFlavors {
        create("bundledOcr") {
            dimension = "ocr"
            buildConfigField("String", "OCR_VARIANT", "\"bundled\"")
        }
        create("thinOcr") {
            dimension = "ocr"
            buildConfigField("String", "OCR_VARIANT", "\"thin\"")
            versionNameSuffix = "-thin"
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Opt-in de APIs inestables de Compose/Material3 usadas en el HUD.
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "DebugProbesKt.bin")
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // CameraX: preview 720p + captura event-driven. ImageAnalysis queda intencionalmente sin usar.
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Persistencia minima: DataStore prefs + SQLite directo (sin Room, ahorra ~1.5MB y kapt/ksp).
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.framework)

    // Arranque rapido (baseline profiles) + splash ligero.
    implementation(libs.profileinstaller)
    implementation(libs.core.splashscreen)

    bundledOcrImplementation(libs.mlkit.text.bundled)
    thinOcrImplementation(libs.mlkit.text.thin)

    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
