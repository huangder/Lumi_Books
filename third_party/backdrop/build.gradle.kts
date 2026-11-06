plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kyant.backdrop"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
dependencies {
    api(platform("androidx.compose:compose-bom:2026.06.01"))
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.ui:ui-graphics")
    api("io.github.kyant0:shapes:1.2.0")
}
