package dev.chieppa.squash.screens.fennec

import dev.chieppa.squash.screens.shell.SquashFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

private const val SAVED_FILE = "../examples/$FENNEC_DRESSED_RESOURCE"
private const val UPDATE_PROPERTY = "squash.updateGoldens"

/** The saved dressed fennec stays what its commands make. Regenerate with ./gradlew :screens-core:test -PupdateGoldens and review the diff. */
class FennecDressedFixtureTest {
    @Test
    fun theSavedDocumentIsWhatTheCommandsMake() {
        val file = File(SAVED_FILE)
        val text = fennecText(fennecDressedDocument())
        if (System.getProperty(UPDATE_PROPERTY) != null) {
            file.parentFile.mkdirs()
            file.writeText(text)
            return
        }
        assertEquals(text, file.readText().replace("\r\n", "\n"))
        assertEquals(fennecDressedDocument(), savedFennecDressed())
    }

    @Test
    fun theExampleOpensAsASavedFile() {
        assertEquals(fennecDressedDocument(), SquashFormat().decode(File(SAVED_FILE).readBytes()))
    }
}
