package dev.chieppa.squash.screens.cat

import dev.chieppa.squash.screens.shell.SquashFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

private const val SAVED_FILE = "../examples/$CAT_DRESSED_RESOURCE"
private const val UPDATE_PROPERTY = "squash.updateGoldens"

/** The saved dressed cat stays what its commands make. Regenerate with ./gradlew :screens-core:test -PupdateGoldens and review the diff. */
class CatDressedFixtureTest {
    @Test
    fun theSavedDocumentIsWhatTheCommandsMake() {
        val file = File(SAVED_FILE)
        val text = catText(catDressedDocument())
        if (System.getProperty(UPDATE_PROPERTY) != null) {
            file.parentFile.mkdirs()
            file.writeText(text)
            return
        }
        assertEquals(text, file.readText().replace("\r\n", "\n"))
        assertEquals(catDressedDocument(), savedCatDressed())
    }

    @Test
    fun theExampleOpensAsASavedFile() {
        assertEquals(catDressedDocument(), SquashFormat().decode(File(SAVED_FILE).readBytes()))
    }

    @Test
    fun eachOutfitWearsItsGarments() {
        val wardrobe = catDressedDocument().character.wardrobe
        val worn = wardrobe.outfits.associate { it.id to it.picks.values.toSet() }
        assertEquals(setOf(CAPE, FANGS), worn[VAMPIRE])
        assertEquals(setOf(SAUSAGE, BUN), worn[HOTDOG])
        assertEquals(emptySet(), worn[CAT_BARE])
    }
}
