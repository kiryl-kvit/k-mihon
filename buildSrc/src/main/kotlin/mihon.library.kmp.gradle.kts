import mihon.buildlogic.configureKotlinCompilation

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")

    id("mihon.code.lint")
}

configureKotlinCompilation()
