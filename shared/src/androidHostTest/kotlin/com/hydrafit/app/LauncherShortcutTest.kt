package com.hydrafit.app

import com.hydrafit.app.core.navigation.AppTab
import com.hydrafit.app.core.navigation.OPEN_TAB_EXTRA
import com.hydrafit.app.core.navigation.appTabForRequest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Train launcher shortcut is declared in XML, so nothing in Kotlin would notice if the extra
 * name, the tab name, the target class or the manifest hook drifted. This ties them together and
 * proves the shortcut can only name a tab, never an action.
 */
class LauncherShortcutTest {

    private val root = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val shortcuts = File(root, "androidApp/src/main/res/xml/shortcuts.xml").readText()

    @Test
    fun theShortcutRequestsTheTrainTabThroughTheNotificationExtra() {
        val extra = Regex("""android:name="([^"]+)"\s+android:value="([^"]+)"""")
            .find(shortcuts)
        val name = requireNotNull(extra).groupValues[1]
        val value = extra.groupValues[2]

        assertEquals(OPEN_TAB_EXTRA, name)
        assertEquals(AppTab.LOG, appTabForRequest(value))
        assertEquals(1, Regex("<extra").findAll(shortcuts).count())
    }

    @Test
    fun theShortcutTargetsTheMainActivityOfThisApp() {
        val gradle = File(root, "androidApp/build.gradle.kts").readText()
        val applicationId = Regex("""applicationId = "([^"]+)"""").find(gradle)!!.groupValues[1]

        assertTrue(shortcuts.contains("android:targetPackage=\"$applicationId\""))
        assertTrue(shortcuts.contains("android:targetClass=\"$applicationId.MainActivity\""))
        assertTrue(
            File(root, "androidApp/src/main/kotlin/com/hydrafit/app/MainActivity.kt").exists()
        )
    }

    @Test
    fun theLauncherActivityPointsAtTheShortcutsFile() {
        val manifest = File(root, "androidApp/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:name=\"android.app.shortcuts\""))
        assertTrue(manifest.contains("android:resource=\"@xml/shortcuts\""))
        assertTrue(
            manifest.indexOf("android.app.shortcuts") >
                manifest.indexOf("android.intent.category.LAUNCHER")
        )
    }
}
