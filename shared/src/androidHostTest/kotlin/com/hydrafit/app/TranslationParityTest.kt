package com.hydrafit.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * Every language the app offers has to cover every English string, keep the same placeholders and
 * carry the plural forms its language needs. A missing key would silently fall back to English on
 * screen, so this reads the resource files themselves rather than trusting a build.
 */
class TranslationParityTest {
    private val pluralForms = mapOf(
        "de" to setOf("one", "other"),
        "cs" to setOf("one", "few", "many", "other"),
        "hu" to setOf("one", "other"),
        "nl" to setOf("one", "other")
    )

    private data class Entry(val plural: Boolean, val text: String, val forms: Map<String, String>)

    @Test
    fun everyLanguageCoversEveryEnglishString() {
        val baseFiles = resourceDirectories().map {
            File(it, "values/strings.xml")
        }.filter { it.exists() }
        assertTrue(baseFiles.isNotEmpty(), "No English resource files were found")
        val problems = mutableListOf<String>()
        for (base in baseFiles) {
            val english = read(base)
            for ((language, forms) in pluralForms) {
                val translated = File(base.parentFile.parentFile, "values-$language/strings.xml")
                if (!translated.exists()) {
                    problems += "${translated.relativeTo(repositoryRoot())} is missing"
                    continue
                }
                compare(language, translated, english, read(translated), forms, problems)
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private fun compare(
        language: String,
        file: File,
        english: Map<String, Entry>,
        translated: Map<String, Entry>,
        forms: Set<String>,
        problems: MutableList<String>
    ) {
        val where = "$language ${file.parentFile.parentFile.parentFile.parentFile.parentFile.name}"
        for ((key, source) in english) {
            val target = translated[key]
            if (target == null) {
                problems += "$where: missing $key"
                continue
            }
            if (source.plural != target.plural) {
                problems += "$where: $key changes between string and plurals"
                continue
            }
            if (!source.plural) {
                if (placeholders(source.text) != placeholders(target.text)) {
                    problems += "$where: $key placeholders differ"
                }
            } else {
                if (target.forms.keys != forms) {
                    problems += "$where: $key has plural forms ${target.forms.keys}, needs $forms"
                }
                val expected = placeholders(source.forms.getValue("other"))
                target.forms.forEach { (quantity, text) ->
                    if (placeholders(text) !=
                        expected
                    ) {
                        problems += "$where: $key/$quantity placeholders differ"
                    }
                }
            }
        }
        (translated.keys - english.keys).forEach { problems += "$where: $it is not in English" }
    }

    private fun placeholders(text: String): List<String> =
        Regex("%\\d\\$[a-z]").findAll(text).map { it.value }.sorted().toList()

    private fun read(file: File): Map<String, Entry> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            file
        ).documentElement
        val result = linkedMapOf<String, Entry>()
        val children = root.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index) as? Element ?: continue
            val name = node.getAttribute("name")
            when (node.tagName) {
                "string" -> result[name] = Entry(false, node.textContent, emptyMap())
                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    val forms = (0 until items.length).associate {
                        val item = items.item(it) as Element
                        item.getAttribute("quantity") to item.textContent
                    }
                    result[name] = Entry(true, "", forms)
                }
            }
        }
        return result
    }

    private fun resourceDirectories(): List<File> = repositoryRoot().walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
        .filter { it.isDirectory && it.name == "composeResources" }
        .toList()

    private fun repositoryRoot(): File {
        var directory: File? = File(System.getProperty("user.dir")).absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").exists()) {
            directory = directory.parentFile
        }
        return checkNotNull(directory) {
            "Could not find the repository root from ${System.getProperty("user.dir")}"
        }
    }
}
