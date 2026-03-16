import com.android.build.api.dsl.LibraryExtension
import mihon.buildlogic.configureAndroid
import mihon.buildlogic.configureTest
import org.gradle.kotlin.dsl.configure

plugins {
    id("com.android.library")

    id("mihon.code.lint")
}

extensions.configure<LibraryExtension> {
    configureAndroid(this)
    configureTest()
}
