import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)
}

kotlin {
    android {
        namespace = "eu.kanade.tachiyomi.source"

        defaultConfig {
            consumerProguardFile("consumer-proguard.pro")
        }
    }

    sourceSets {
        commonMain {
            kotlin.setSrcDirs(emptyList<String>())
        }
        androidMain {
            kotlin.setSrcDirs(emptyList<String>())
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

dependencies {
    api(projects.extensionsLib)
}
