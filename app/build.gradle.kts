import java.util.Date
import kotlin.random.Random

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.bugsnag.android.gradle")
    id("com.google.devtools.ksp")
    id("com.meta.spatial.plugin")
}

fun calculateVersionCode(): Int {
    return (Date().time / 1000).toInt()
}

fun calculateVersion(vCode: Int): String {
    val versionFile = File(project.rootDir, "app/src/main/assets/libopenmw/resources/version")
    val firstLine = versionFile.inputStream().bufferedReader().use { it.readLine() } ?: "0"
    return "${firstLine.trim()}-$vCode"
}

android {
    namespace = "com.libopenmw.openmw"
    compileSdk = 30
    ndkVersion = "27.3.13750724"

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_21
        sourceCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    sourceSets {
        getByName("main") {
            resources {
                // To include the wrap.sh script required by ASAN
                srcDir("wrap/res")
            }
        }
    }

    defaultConfig {
        applicationId = "com.queststoredb.openmw_quest"

        val vCode = calculateVersionCode()
        versionCode = vCode
        versionName = calculateVersion(vCode)

        minSdk = 29
        // Do not update past 29 -- see https://github.com/xyzz/openmw-android/issues/30
        targetSdk = 29

        buildConfigField("int", "RANDOMIZER", Random.nextInt(0, 999).toString())

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }
    }

    lint {
        checkReleaseBuilds = false
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }

        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    flavorDimensions += listOf("version", "device")

    productFlavors {
        create("nightly") {
            dimension = "version"
            applicationId = "com.queststoredb.openmw_quest"
            versionNameSuffix = "-nightly"
        }
        create("mobile") {
            dimension = "device"
        }
        create("quest") {
            dimension = "device"
            isDefault = true
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "omw_${variant.buildType.name}_${variant.versionName}.apk"
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
        }
    }
}

val metaSpatialSdkVersion = "0.11.1"
dependencies {
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("androidx.preference:preference:1.1.1")
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.recyclerview:recyclerview:1.2.1")

    implementation(project(":storagechooser"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.4.0")

    implementation(files("lib/commons-base-0.10.8.aar"))
    implementation(files("lib/anko-commons-0.10.8.aar"))
    implementation(files("lib/sqlite-base-0.10.8.aar"))
    implementation(files("lib/anko-sqlite-0.10.8.aar"))

    implementation("com.bugsnag:bugsnag-android-ndk:5.32.4")

    testImplementation("junit:junit:4.13.2")

    implementation("com.meta.spatial:meta-spatial-sdk:$metaSpatialSdkVersion")
    implementation ("com.meta.spatial:meta-spatial-sdk-vr:${metaSpatialSdkVersion}")
    implementation("com.meta.spatial:meta-spatial-sdk-toolkit:$metaSpatialSdkVersion")
    implementation("com.meta.spatial:meta-spatial-sdk-castinputforward:$metaSpatialSdkVersion")
}

val projectDir = layout.projectDirectory
val sceneDirectory = projectDir.dir("quest_spatial")
spatial {
    allowUsageDataCollection = false
    scenes {
        exportItems {
            item {
                projectPath.set(sceneDirectory.file("Main.metaspatial"))
                outputPath.set(projectDir.dir("src/quest/assets/scenes"))
            }
        }
    }
}
