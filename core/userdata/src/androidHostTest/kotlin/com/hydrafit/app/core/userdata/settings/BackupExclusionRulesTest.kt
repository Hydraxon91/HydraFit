package com.hydrafit.app.core.userdata.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Keystore-wrapped preference files must stay out of Android Auto Backup and device transfer:
 * their values are encrypted with a key that does not move to another device. This ties the two
 * exclusion XML files to the preference names the stores actually use, so a rename cannot silently
 * put a secret back into a backup.
 */
class BackupExclusionRulesTest {

    private val root = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val secureFiles = mapOf(
        "hydrafit_groq_secure" to
            "core/userdata/src/androidMain/kotlin/com/hydrafit/app/core/userdata/settings/" +
            "AndroidGroqApiKeyStore.kt",
        "hydrafit_secure" to
            "core/userdata/src/androidMain/kotlin/com/hydrafit/app/core/userdata/settings/" +
            "AndroidKeystoreApiKeyStore.kt",
        "hydrafit_backup_secure" to
            "core/userdata/src/androidMain/kotlin/com/hydrafit/app/core/userdata/backup/" +
            "AndroidBackupEncryptionKeyStore.kt"
    )

    @Test
    fun theStoresStillUseTheExcludedPreferenceNames() {
        secureFiles.forEach { (name, source) ->
            assertTrue(
                File(root, source).readText().contains("\"$name\""),
                "$source no longer uses $name"
            )
        }
    }

    @Test
    fun everySecurePreferenceFileIsExcludedFromEveryBackupPath() {
        val extraction = File(
            root,
            "androidApp/src/main/res/xml/data_extraction_rules.xml"
        ).readText()
        val cloud = extraction.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        val transfer = extraction.substringAfter(
            "<device-transfer>"
        ).substringBefore("</device-transfer>")
        val legacy = File(root, "androidApp/src/main/res/xml/backup_rules.xml").readText()
        secureFiles.keys.forEach { name ->
            val rule = "domain=\"sharedpref\" path=\"$name.xml\""
            assertTrue(cloud.contains(rule), "cloud-backup does not exclude $name")
            assertTrue(transfer.contains(rule), "device-transfer does not exclude $name")
            assertTrue(legacy.contains(rule), "backup_rules does not exclude $name")
        }
    }

    @Test
    fun theManifestPointsAtBothRuleFiles() {
        val manifest = File(root, "androidApp/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
    }
}
