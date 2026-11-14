pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

// Automatic adaptation when building on GitHub Actions / standard JDK 21 + SDK 35 + AGP 8.7.3 environments
// (Outside AI Studio's internal android-36.1 container)
val isAiStudioContainer = file("/opt/android/sdk/platforms/android-36.1").exists()
if (!isAiStudioContainer) {
  val envFile = file(".env")
  val envExample = file(".env.example")
  if (!envFile.exists() && envExample.exists()) {
    envFile.writeText(envExample.readText())
  }

  val debugKs = file("debug.keystore")
  val debugKsB64 = file("debug.keystore.base64")
  if (!debugKs.exists() && debugKsB64.exists()) {
    runCatching {
      val cleanB64 = debugKsB64.readText().replace("\\s".toRegex(), "")
      debugKs.writeBytes(java.util.Base64.getDecoder().decode(cleanB64))
    }
  }

  val tomlFile = file("gradle/libs.versions.toml")
  if (tomlFile.exists()) {
    var toml = tomlFile.readText()
    val replacements = mapOf(
      "agp = \"9.1.1\"" to "agp = \"8.7.3\"",
      "coreKtx = \"1.18.0\"" to "coreKtx = \"1.15.0\"",
      "junitVersion = \"1.3.0\"" to "junitVersion = \"1.2.1\"",
      "espressoCore = \"3.7.0\"" to "espressoCore = \"3.6.1\"",
      "activityCompose = \"1.10.1\"" to "activityCompose = \"1.9.3\"",
      "kotlin = \"2.2.10\"" to "kotlin = \"2.0.21\"",
      "googleDevtoolsKsp = \"2.3.5\"" to "googleDevtoolsKsp = \"2.0.21-1.0.28\"",
      "navigationCompose = \"2.8.9\"" to "navigationCompose = \"2.8.5\"",
      "roomRuntime = \"2.7.0\"" to "roomRuntime = \"2.6.1\"",
      "roomKtx = \"2.7.0\"" to "roomKtx = \"2.6.1\"",
      "roomCompiler = \"2.7.0\"" to "roomCompiler = \"2.6.1\"",
      "kotlinxCoroutinesTest = \"1.10.2\"" to "kotlinxCoroutinesTest = \"1.9.0\"",
      "kotlinxCoroutinesAndroid = \"1.10.2\"" to "kotlinxCoroutinesAndroid = \"1.9.0\"",
      "kotlinxCoroutinesCore = \"1.10.2\"" to "kotlinxCoroutinesCore = \"1.9.0\"",
      "robolectric = \"4.16.1\"" to "robolectric = \"4.14.1\"",
      "googleServices = \"4.5.0\"" to "googleServices = \"4.4.2\""
    )
    for ((oldVal, newVal) in replacements) {
      toml = toml.replace(oldVal, newVal)
    }
    if (!toml.contains("kotlin-android =")) {
      toml = toml.replace(
        "[plugins]\n",
        "[plugins]\nkotlin-android = { id = \"org.jetbrains.kotlin.android\", version.ref = \"kotlin\" }\n"
      )
    }
    tomlFile.writeText(toml)
  }

  val rootBuild = file("build.gradle.kts")
  if (rootBuild.exists()) {
    var rtext = rootBuild.readText()
    if (!rtext.contains("libs.plugins.kotlin.android")) {
      rtext = rtext.replace(
        "alias(libs.plugins.android.application) apply false",
        "alias(libs.plugins.android.application) apply false\n  alias(libs.plugins.kotlin.android) apply false"
      )
    }
    rtext = rtext.replace(
      "alias(libs.plugins.google.services) apply false",
      "// alias(libs.plugins.google.services) apply false"
    )
    rootBuild.writeText(rtext)
  }

  val appBuild = file("app/build.gradle.kts")
  if (appBuild.exists()) {
    var atext = appBuild.readText()
    atext = atext.replace(
      "import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy",
      "// import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy"
    )
    if (!atext.contains("libs.plugins.kotlin.android")) {
      atext = atext.replace(
        "alias(libs.plugins.android.application)",
        "alias(libs.plugins.android.application)\n  alias(libs.plugins.kotlin.android)"
      )
    }
    atext = atext.replace("alias(libs.plugins.google.services)", "// alias(libs.plugins.google.services)")
    atext = atext.replace(
      "compileSdk { version = release(36) { minorApiLevel = 1 } }",
      "compileSdk = 35"
    )
    atext = atext.replace("targetSdk = 36", "targetSdk = 35")
    if (!atext.contains("kotlinOptions")) {
      atext = atext.replace(
        "targetCompatibility = JavaVersion.VERSION_11\n  }",
        "targetCompatibility = JavaVersion.VERSION_11\n  }\n  kotlinOptions {\n    jvmTarget = \"11\"\n  }"
      )
    }
    atext = atext.replace(
      "googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }",
      "// googleServices omitted in CI"
    )
    // Fallback release signing if KEYSTORE_PATH / my-upload-key.jks is not provided in CI
    val ksEnv = System.getenv("KEYSTORE_PATH")
    val relKsExists = if (!ksEnv.isNullOrBlank()) file(ksEnv).exists() else file("my-upload-key.jks").exists()
    if (!relKsExists && debugKs.exists()) {
      atext = atext.replace(
        "val keystorePath = System.getenv(\"KEYSTORE_PATH\") ?: \"\${rootDir}/my-upload-key.jks\"\n      storeFile = file(keystorePath)\n      storePassword = System.getenv(\"STORE_PASSWORD\")\n      keyAlias = \"upload\"\n      keyPassword = System.getenv(\"KEY_PASSWORD\")",
        "storeFile = file(\"\${rootDir}/debug.keystore\")\n      storePassword = \"android\"\n      keyAlias = \"androiddebugkey\"\n      keyPassword = \"android\""
      )
    }
    appBuild.writeText(atext)
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "ASiS pannel"

include(":app")
