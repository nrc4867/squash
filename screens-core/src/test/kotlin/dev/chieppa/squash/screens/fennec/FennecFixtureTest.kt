package dev.chieppa.squash.screens.fennec

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

private const val SAVED_FILE = "src/testFixtures/resources/$FENNEC_RESOURCE"
private const val UPDATE_PROPERTY = "squash.updateGoldens"

/** The saved fennec stays what its commands make. Regenerate with ./gradlew :screens-core:test -PupdateGoldens and review the diff. */
class FennecFixtureTest {
    @Test
    fun theSavedDocumentIsWhatTheCommandsMake() {
        val file = File(SAVED_FILE)
        val text = fennecText(fennecDocument())
        if (System.getProperty(UPDATE_PROPERTY) != null) {
            file.parentFile.mkdirs()
            file.writeText(text)
            return
        }
        assertEquals(text, file.readText().replace("\r\n", "\n"))
        assertEquals(fennecDocument(), savedFennec())
    }
}
