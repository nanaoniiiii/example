// Top-level build file
buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}

subprojects {
    afterEvaluate {
        configurations.all {
            resolutionStrategy {
                force(
                    "androidx.core:core:1.12.0",
                    "androidx.core:core-ktx:1.12.0",
                    "androidx.annotation:annotation:1.7.1",
                    "org.jetbrains.kotlin:kotlin-stdlib:1.9.20",
                    "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.20",
                    "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20",
                )
            }
        }
    }
}
