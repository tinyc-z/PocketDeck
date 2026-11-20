import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val appVersionName = "0.5.1"

val gitCommitCount: Int? = if (rootProject.file(".git").exists()) {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.orNull?.trim()?.toIntOrNull()
} else {
    null
}

val gitShortHash: String = if (rootProject.file(".git").exists()) {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.orNull?.trim()?.takeIf { it.isNotEmpty() } ?: "local"
} else {
    "local"
}

val nowTime = LocalDateTime.now()
val buildTimeStr: String = nowTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
val dateBuildSeq: String = nowTime.format(DateTimeFormatter.ofPattern("yyyyMMddHH"))

val buildNumberStr: String = providers.gradleProperty("POCKETDECK_BUILD_NUMBER").orNull
    ?: providers.environmentVariable("POCKETDECK_BUILD_NUMBER").orNull
    ?: gitCommitCount?.let { "$it ($gitShortHash)" }
    ?: dateBuildSeq

val resolvedVersionCode: Int = providers.gradleProperty("POCKETDECK_VERSION_CODE").orNull?.toIntOrNull()
    ?: gitCommitCount?.coerceAtLeast(3)
    ?: 3

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.isFile) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

fun signingProp(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey)?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(envKey).orNull?.takeIf { it.isNotBlank() }

val releaseStorePath = signingProp("storeFile", "KEYSTORE_FILE")
val releaseStorePass = signingProp("storePassword", "KEYSTORE_PASSWORD")
val releaseKeyAlias = signingProp("keyAlias", "KEY_ALIAS")
val releaseKeyPass = signingProp("keyPassword", "KEY_PASSWORD")
val hasReleaseSigning = !releaseStorePath.isNullOrBlank() &&
    !releaseStorePass.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPass.isNullOrBlank() &&
    rootProject.file(releaseStorePath).isFile

base {
    archivesName.set("PocketDeck-v$appVersionName-b${gitCommitCount ?: dateBuildSeq}")
}

android {
    namespace = "com.pocketdeck.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pocketdeck.launcher"
        minSdk = 26
        targetSdk = 36
        versionCode = resolvedVersionCode
        versionName = appVersionName

        buildConfigField("String", "BUILD_NUMBER", "\"${buildNumberStr.replace("\"", "\\\"")}\"")
        buildConfigField("String", "BUILD_TIME", "\"$buildTimeStr\"")
        buildConfigField("String", "GIT_HASH", "\"$gitShortHash\"")
        buildConfigField("String", "PROJECT_URL", "\"https://github.com/tinyc-z/PocketDeck\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = releaseStorePass
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPass
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    androidResources {
        localeFilters += listOf("en", "zh-rCN", "zh-rTW")
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/androidx/**",
            "/META-INF/*.version",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*",
            "org/apache/commons/codec/language/bm/**",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.commons.compress)
    implementation(libs.tukaani.xz)
    implementation(libs.junrar)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}
