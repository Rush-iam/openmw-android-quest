plugins {
    id("com.android.library")
}

group = "com.github.codekidX"

android {
    namespace = "com.codekidlabs.storagechooser"
    compileSdk = 30

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_21
        sourceCompatibility = JavaVersion.VERSION_21
    }

    defaultConfig {
        minSdk = 21
        targetSdk = 29

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro"))
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    androidTestImplementation("androidx.test.espresso:espresso-core:3.1.0-alpha4") {
        exclude(group = "com.android.support", module = "support-annotations")
    }

    implementation("androidx.appcompat:appcompat:1.0.0-beta01")
    implementation("com.google.android.material:material:1.0.0-beta01")
    testImplementation("junit:junit:4.12")
}

val javadoc by tasks.registering(Javadoc::class) {
    isFailOnError = false
    val mainSourceSet = android.sourceSets.getByName("main").java.getSourceFiles()
    source(mainSourceSet)

    classpath += files(android.bootClasspath.joinToString(File.pathSeparator) { it.absolutePath })
    configurations.findByName("compileClasspath")?.let {
        classpath += it
    }
}

// Build a jar with javadoc
val javadocJar by tasks.registering(Jar::class) {
    dependsOn(javadoc)
    archiveClassifier.set("javadoc")
    from(javadoc.get().destinationDir)
}

artifacts {
    add("archives", javadocJar)
}
