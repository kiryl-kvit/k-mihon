import com.mikepenz.aboutlibraries.plugin.AboutLibrariesExtension
import com.mikepenz.aboutlibraries.plugin.AboutLibrariesTask
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register

val aboutLibrariesExtension = extensions.findByType<AboutLibrariesExtension>()
    ?: extensions.create<AboutLibrariesExtension>("aboutLibraries").apply {
        applyConvention()
    }

tasks.register<AboutLibrariesTask>("exportLibraryDefinitions") {
    group = ""
    configureOutputFile(
        layout.buildDirectory.dir("generated/aboutLibraries").map { it.file("aboutlibraries.json") },
    )
    configure()
}
