buildscript {
    val kotlinVersion by extra("1.9.22")
    val spatialsdkVersion by extra("0.10.1")

    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinVersion}")
        classpath("com.bugsnag:bugsnag-android-gradle-plugin:8.2.0")
        classpath("com.meta.spatial.plugin:com.meta.spatial.plugin.gradle.plugin:${spatialsdkVersion}")
        classpath("com.meta.spatial:meta-spatial-sdk-castinputforward:${spatialsdkVersion}")
    }
}

plugins {
    id("com.android.application") version "8.1.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("com.meta.spatial.plugin") version "0.10.1" apply true
    id("com.google.devtools.ksp") version "2.0.20-1.0.24" apply true

    idea
}

idea {
    module {
        excludeDirs.add(file("buildscripts/"))
    }
}
