import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget

plugins {
    alias(mihonx.plugins.kotlin.multiplatform)

    alias(libs.plugins.kotlin.serialization)
    id("maven-publish")
}

group = "com.github.kiryl-kvit.k-mihon"
version = providers.gradleProperty("extensionsLibVersion")
    .orElse(System.getenv("VERSION") ?: "local-SNAPSHOT")
    .get()

kotlin {
    android {
        namespace = "eu.kanade.tachiyomi.extensions.lib"
    }

    targets.withType(KotlinAndroidTarget::class.java).configureEach {
        publishLibraryVariants("release")
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    @Suppress("UnstableApiUsage")
    dependencies {
        api(libs.injekt)
        api(libs.jsoup)
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.serialization.json)
        api(libs.kotlinx.serialization.jsonOkio)
        api(libs.okhttp.core)
        api(libs.okio)
        api(libs.rxJava)

        implementation(platform(libs.androidx.compose.bom))
        implementation(libs.androidx.compose.runtime)
    }

    sourceSets {
        androidMain {
            dependencies {
                implementation(projects.core.common)
                implementation(projects.i18n)
                api(libs.androidx.preference)
                implementation(libs.androidx.annotation)
                implementation(libs.okhttp.brotli)
                implementation(libs.okhttp.dnsOverHttps)
                implementation(libs.okhttp.logging)
                implementation(libs.quickJs)
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xexpect-actual-classes",
        )
    }
}
