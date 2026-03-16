import com.android.build.api.dsl.TestExtension
import mihon.buildlogic.configureAndroid
import mihon.buildlogic.configureTest
import org.gradle.kotlin.dsl.configure

plugins {
    id("com.android.test")

    id("mihon.code.lint")
}

extensions.configure<TestExtension> {
    configureAndroid(this)
    configureTest()
}
