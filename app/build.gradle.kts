import java.util.Properties
import java.io.FileInputStream

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.arwora"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.arworahyper.toolkit"
    minSdk = 24
    targetSdk = 35
    versionCode = 192
    versionName = "1.49.31"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePropertiesFile = rootProject.file("keystore.properties")
      if (keystorePropertiesFile.exists()) {
        val keystoreProperties = Properties()
        FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
        
        storeFile = file(keystoreProperties.getProperty("storeFile") ?: "${rootDir}/my-upload-key.jks")
        storePassword = keystoreProperties.getProperty("storePassword")
        keyAlias = keystoreProperties.getProperty("keyAlias")
        keyPassword = keystoreProperties.getProperty("keyPassword")
      } else {
        // Fallback to environment variables or debug key
        val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
        val keystoreFile = file(keystorePath)
        if (keystoreFile.exists()) {
          storeFile = keystoreFile
          storePassword = System.getenv("STORE_PASSWORD")
          keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
          keyPassword = System.getenv("KEY_PASSWORD")
        } else {
          storeFile = file("${rootDir}/debug.keystore")
          storePassword = "android"
          keyAlias = "androiddebugkey"
          keyPassword = "android"
        }
      }
      enableV1Signing = true
      enableV2Signing = true
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
      enableV1Signing = true
      enableV2Signing = true
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      val customDebugKeystore = file("${rootDir}/debug.keystore")
      if (customDebugKeystore.exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}


// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.recyclerview)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.material)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.jmdns)
  implementation(libs.jcifs.ng)
  implementation(libs.security.crypto)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation("androidx.work:work-runtime-ktx:2.9.0")
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  implementation(libs.osmdroid.android)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation("androidx.exifinterface:exifinterface:1.3.7")
  implementation(libs.mlkit.text.recognition)
  implementation(libs.youtubedl.android)
  implementation(libs.ffmpeg.android)
  implementation(libs.apksig)
  // implementation("me.legrange:mikrotik:3.0")
  implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
