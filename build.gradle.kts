buildscript {
    val kotlinVersion by extra("2.0.21")
    val spatialsdkVersion by extra("0.11.1")

    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinVersion}")
        classpath("com.bugsnag:bugsnag-android-gradle-plugin:8.2.0")
        classpath("com.meta.spatial.plugin:com.meta.spatial.plugin.gradle.plugin:${spatialsdkVersion}")
        classpath("com.meta.spatial:meta-spatial-sdk-castinputforward:${spatialsdkVersion}")
    }
}

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.meta.spatial.plugin") version "0.11.1" apply true
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply true

    idea
}

idea {
    module {
        excludeDirs.add(file("buildscripts/"))
    }
}
