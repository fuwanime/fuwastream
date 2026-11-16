plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
  alias(libs.plugins.androidx.baselineprofile)
}

// CI injects the release keystore through these env vars (see .github/workflows/build.yml).
// Release builds never fall back to the debug key: a different certificate would break in-place updates.
val releaseKeystore = System.getenv("KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.exists() }
val releasePassword = System.getenv("KEYSTORE_PASSWORD")
val releaseAlias = System.getenv("KEY_ALIAS") ?: "fuwanime"
// Only packaging tasks need signing. Match real task names so values like
// `--tests com.fuwanime.MangaReleaseMappingTest` never trigger the release check.
val buildingRelease = gradle.startParameter.taskNames.any { it.substringAfterLast(':').endsWith("Release") }
val allowUnsignedRelease = System.getenv("GITHUB_EVENT_NAME") == "pull_request" || System.getenv("ALLOW_UNSIGNED_RELEASE") == "true"
if (buildingRelease && (releaseKeystore == null || releasePassword.isNullOrBlank()) && !allowUnsignedRelease) {
  throw GradleException(
    "Release signing is not configured. Set KEYSTORE_PATH, KEYSTORE_PASSWORD and KEY_ALIAS " +
      "to the permanent FuwaStream keystore before building a release.",
  )
}

android {
  namespace = "com.fuwanime"
  compileSdk = 37
  // A call above minSdk crashes old phones at runtime; fail the release build instead (lintVital runs fatal checks).
  lint { fatal += "NewApi" }

  defaultConfig {
    applicationId = "com.fuwanime.app"
    minSdk = 24
    targetSdk = 37
    // F-Droid & CI pass VERSION_CODE/VERSION_NAME as env vars. Fallback to fixed
    // values when not set (deterministic, not epoch minutes).
    versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 13
    versionName = System.getenv("VERSION_NAME") ?: "2.1.36"
    // Instrumented tests exercise the real Comix OCR/translation pipeline on an emulator.
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // Where the in-app updater looks for releases. Pinned to the official repo: a fork build or CI run must not
    // point users at another repo (UPDATE_REPO overrides it for a fork that ships its own releases).
    buildConfigField("String", "UPDATE_REPO", "\"${System.getenv("UPDATE_REPO") ?: "fuwanime/fuwastream"}\"")
  }

  signingConfigs {
    if (releaseKeystore != null && !releasePassword.isNullOrBlank()) {
      create("release") {
        storeFile = releaseKeystore
        storePassword = releasePassword
        keyAlias = releaseAlias
        keyPassword = releasePassword
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release")
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
  }
  splits {
    abi {
      // Releases pass -PabiSplits=false for one universal APK, which the in-app updater installs.
      isEnable = (findProperty("abiSplits") as String?)?.toBooleanStrictOrNull() ?: true
      reset()
      // x86_64 keeps the CI emulator smoke test installable; phones use the arm64 build.
      include("arm64-v8a", "armeabi-v7a", "x86_64")
      isUniversalApk = false
    }
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
  implementation(libs.androidx.palette.ktx)
  implementation(libs.haze)
  implementation(libs.coil.network.okhttp)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.media3.datasource.okhttp)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.exoplayer.hls)
  implementation(libs.media3.ui)
  implementation(libs.media3.session)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.androidx.profileinstaller)
  baselineProfile(project(":baselineprofile"))
  implementation(libs.okhttp.logging)

  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.moshi)
  ksp(libs.androidx.room.compiler)
  ksp(libs.moshi.kotlin.codegen)
  testImplementation(libs.junit)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.runner)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
