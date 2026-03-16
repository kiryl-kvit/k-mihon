import com.android.build.api.dsl.ApplicationExtension
import mihon.buildlogic.AndroidConfig
import mihon.buildlogic.configureAndroid
import mihon.buildlogic.configureTest
import org.gradle.kotlin.dsl.configure

plugins {
    id("com.android.application")

    id("mihon.code.lint")
}

extensions.configure<ApplicationExtension> {
    defaultConfig {
        targetSdk = AndroidConfig.TARGET_SDK
    }
    configureAndroid(this)
    configureTest()
}
