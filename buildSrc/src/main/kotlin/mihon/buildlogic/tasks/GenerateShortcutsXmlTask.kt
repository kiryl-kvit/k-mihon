package mihon.buildlogic.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

abstract class GenerateShortcutsXmlTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFile: RegularFileProperty

    @get:Input
    abstract val applicationId: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val documentBuilder = documentBuilderFactory.newDocumentBuilder()
        val document = sourceFile.get().asFile.inputStream().use(documentBuilder::parse)

        val shortcuts = document.getElementsByTagName("shortcut")
        for (shortcutIndex in 0 until shortcuts.length) {
            val shortcut = shortcuts.item(shortcutIndex)
            val intents = shortcut.childNodes
            for (intentIndex in 0 until intents.length) {
                val intent = intents.item(intentIndex)
                if (intent.nodeName != "intent") continue
                intent.attributes.setNamedItemNS(
                    document.createAttributeNS(ANDROID_URI, "android:targetPackage").apply {
                        value = applicationId.get()
                    },
                )
            }
        }

        val outputFile = outputDirectory.file("xml/${sourceFile.get().asFile.name}").get().asFile
        outputFile.parentFile.mkdirs()

        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.METHOD, "xml")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
        }.transform(DOMSource(document), StreamResult(outputFile))
    }

    private companion object {
        const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    }
}
