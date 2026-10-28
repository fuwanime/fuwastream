plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
}

// CI injects the release keystore through these env vars (see .github/workflows/build.yml).
// Without them, release falls back to the debug key so local builds still work.
val releaseKeystore = System.getenv("KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.exists() }

android {
  namespace = "com.fuwanime"
  compileSdk = 36

  defaultConfig {
      applicationId = "com.fuwanime.app"
      minSdk = 24
      targetSdk = 36
      // CI's run_number reset when the repo was recreated (some devices still hold code 14),
      // so small values are ignored and the code falls back to minutes since epoch — always rising.
      versionCode = System.getenv("VERSION_CODE")?.toIntOrNull()
          ?.takeIf { it > 1_000_000 }
          ?: (System.currentTimeMillis() / 60_000L).toInt()
      versionName = System.getenv("VERSION_NAME") ?: "2.0.0"
      // The in-app update checker looks at the repo this APK was built from (a fork checks its own releases).
      buildConfigField("String", "UPDATE_REPO", "\"${System.getenv("GITHUB_REPOSITORY") ?: "fuwanime/fuwastream"}\"")
  }

  signingConfigs {
    if (releaseKeystore != null) {
      create("release") {
        storeFile = releaseKeystore
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "fuwanime"
        keyPassword = System.getenv("KEYSTORE_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
    }
    debug {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.media3.datasource.okhttp)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.exoplayer.hls)
  implementation(libs.media3.ui)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)
  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.moshi)
  ksp(libs.androidx.room.compiler)
  ksp(libs.moshi.kotlin.codegen)
  testImplementation(libs.junit)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
