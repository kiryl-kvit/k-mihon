import mihon.buildlogic.AndroidConfig
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("mihon.library.kmp")
    alias(libs.plugins.moko)
}

kotlin {
    android {
        namespace = "tachiyomi.i18n"
        compileSdk = AndroidConfig.COMPILE_SDK
        minSdk = AndroidConfig.MIN_SDK

        withHostTest {}

        androidResources {
            enable = true
        }

        lint {
            disable.addAll(listOf("MissingTranslation", "ExtraTranslation"))
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                api(libs.moko.core)
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

multiplatformResources {
    resourcesPackage.set("tachiyomi.i18n")
}
