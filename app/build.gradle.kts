import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionProps = Properties().apply {
    val file = rootProject.file("version.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val appVersionName = versionProps.getProperty("VERSION_NAME") ?: "0.0.0"
val appVersionCode = versionProps.getProperty("VERSION_CODE")?.toIntOrNull() ?: 1
val generatedBuildValuesDir = layout.buildDirectory.dir("generated/source/buildValues/main")
val hasReleaseSigning = !System.getenv("WUWA_RELEASE_STORE_FILE").isNullOrBlank() &&
    !System.getenv("WUWA_RELEASE_STORE_PASSWORD").isNullOrBlank() &&
    !System.getenv("WUWA_RELEASE_KEY_ALIAS").isNullOrBlank() &&
    !System.getenv("WUWA_RELEASE_KEY_PASSWORD").isNullOrBlank()

val generateBuildValues by tasks.registering {
    inputs.property("versionName", appVersionName)
    inputs.property("versionCode", appVersionCode)
    outputs.dir(generatedBuildValuesDir)

    doLast {
        val outputDir = generatedBuildValuesDir.get().asFile.resolve("com/acceleratorer/wuwavn")
        outputDir.mkdirs()
        outputDir.resolve("BuildValues.java").writeText(
            """
            package com.acceleratorer.wuwavn;

            final class BuildValues {
                public static final String VERSION_NAME = "$appVersionName";
                public static final int VERSION_CODE = $appVersionCode;

                private BuildValues() {
                }
            }
            """.trimIndent() + "\n",
            Charsets.UTF_8,
        )
    }
}

android {
    namespace = "com.acceleratorer.wuwavn"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.acceleratorer.wuwavn"
        minSdk = 30
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        aidl = true
        compose = true
    }

    lint {
        disable += "HardcodedDebugMode"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(System.getenv("WUWA_RELEASE_STORE_FILE"))
                storePassword = System.getenv("WUWA_RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("WUWA_RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("WUWA_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isDebuggable = false
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.srcDirs("src/main/kotlin", "src/main/java", generatedBuildValuesDir)
            res.srcDirs("src/main/res")
            aidl.srcDirs("src/main/aidl")
        }
    }
}

tasks.named("preBuild") {
    dependsOn(generateBuildValues)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("dev.rikka.shizuku:api:13.1.5") {
        exclude(group = "androidx.annotation", module = "annotation")
    }
    implementation("dev.rikka.shizuku:provider:13.1.5") {
        exclude(group = "androidx.annotation", module = "annotation")
    }
    debugImplementation("androidx.compose.ui:ui-tooling")
}
