import com.android.build.api.dsl.ApplicationExtension
import mihon.buildlogic.configureCompose
import org.gradle.kotlin.dsl.configure

plugins {
    id("com.android.application")

    id("mihon.code.lint")
}

extensions.configure<ApplicationExtension> {
    configureCompose(this)
}
