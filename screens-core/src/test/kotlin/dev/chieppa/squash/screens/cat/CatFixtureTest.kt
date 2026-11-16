package dev.chieppa.squash.screens.cat

import dev.chieppa.squash.screens.shell.SquashFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SAVED_FILE = "../examples/$CAT_RESOURCE"
private const val UPDATE_PROPERTY = "squash.updateGoldens"

/** The saved cat stays what its commands make. Regenerate with ./gradlew :screens-core:test -PupdateGoldens and review the diff. */
class CatFixtureTest {
    @Test
    fun theSavedDocumentIsWhatTheCommandsMake() {
        val file = File(SAVED_FILE)
        val text = catText(catDocument())
        if (System.getProperty(UPDATE_PROPERTY) != null) {
            file.parentFile.mkdirs()
            file.writeText(text)
            return
        }
        assertEquals(text, file.readText().replace("\r\n", "\n"))
        assertEquals(catDocument(), savedCat())
    }

    @Test
    fun theExampleOpensAsASavedFile() {
        assertEquals(catDocument(), SquashFormat().decode(File(SAVED_FILE).readBytes()))
    }

    @Test
    fun itIsBuiltAsOneSurfaceWithPaintedEyes() {
        val character = catDocument().character
        assertTrue(character.oneSurface)
        assertEquals(listOf(CAT_EYES), character.paintedEyes.map { it.id })
    }
}
