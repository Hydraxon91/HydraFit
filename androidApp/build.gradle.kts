import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun signingProperty(name: String): String? = localProperties.getProperty(name)
    ?: providers.environmentVariable(name).orNull

val releaseKeystorePath = signingProperty("RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword = signingProperty("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingProperty("RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingProperty("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

val defaultVersionName = "0.12.0-dev"
val defaultVersionCode = 1
val releaseTagPattern = Regex("""^v\d+\.\d+\.\d+([-+].*)?$""")
val stableReleaseTagPattern = Regex("""^v(\d+)\.(\d+)\.(\d+)$""")
val ciVersionName = providers.environmentVariable("GITHUB_REF_NAME").orNull
    ?.takeIf { releaseTagPattern.matches(it) }
    ?.removePrefix("v")
    ?: defaultVersionName

// A stable release tag owns the version code so published releases sort by version, not by publish
// time: major * 1_000_000 + minor * 10_000 + patch * 100 (e.g. v0.8.4 -> 80400). Pre-release tags and
// non-tag builds keep the workflow run number, then the local default.
fun versionCodeForTag(tag: String?): Int? {
    val numbers = tag
        ?.let { stableReleaseTagPattern.matchEntire(it) }
        ?.groupValues
        ?.drop(1)
        ?.mapNotNull { it.toIntOrNull() }
        ?: return null
    if (numbers.size != 3) return null
    val (major, minor, patch) = numbers
    return major * 1_000_000 + minor * 10_000 + patch * 100
}

val ciVersionCode = versionCodeForTag(providers.environmentVariable("GITHUB_REF_NAME").orNull)
    ?: providers.environmentVariable("GITHUB_RUN_NUMBER").orNull
        ?.toIntOrNull()
    ?: defaultVersionCode

/**
 * Runs a Git command in the repository root, returning its trimmed stdout, or null when Git
 * metadata is unavailable (no Git binary, no repository).
 */
fun gitOutput(vararg args: String): String? = try {
    providers.exec {
        commandLine("git", "-C", rootProject.projectDir.absolutePath, *args)
    }.standardOutput.asText.get().trim().ifEmpty { null }
} catch (_: Exception) {
    null
}

// Development build identity: the short revision plus a marker when tracked files are modified.
// Releases (tagged builds) keep their plain tag name; only the debug build type carries this.
val gitRevision = gitOutput("rev-parse", "--short", "HEAD")
val gitTrackedFilesModified = gitOutput("status", "--porcelain", "--untracked-files=no") != null
val devVersionSuffix = "-" + (gitRevision ?: "unavailable") +
    if (gitTrackedFilesModified) "-dirty" else ""

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.hydrafit.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.hydrafit.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = ciVersionCode
        versionName = ciVersionName
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\""
        )
        buildConfigField("String", "VERSION_NAME", "\"$ciVersionName\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        debug {
            versionNameSuffix = devVersionSuffix
            buildConfigField("String", "VERSION_NAME", "\"$ciVersionName$devVersionSuffix\"")
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.lifecycle(
                    "Release signing not configured (RELEASE_KEYSTORE_* absent); release will be unsigned."
                )
            }
            ndk {
                abiFilters += "arm64-v8a"
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}
