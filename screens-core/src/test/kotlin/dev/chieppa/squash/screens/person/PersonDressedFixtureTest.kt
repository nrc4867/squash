package dev.chieppa.squash.screens.person

import dev.chieppa.squash.document.worn
import dev.chieppa.squash.document.wornPart
import dev.chieppa.squash.screens.fennec.fennecText
import dev.chieppa.squash.screens.shell.SquashFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val SAVED_FILE = "../examples/$PERSON_DRESSED_RESOURCE"
private const val UPDATE_PROPERTY = "squash.updateGoldens"

/** The saved dressed person stays what its commands make. Regenerate with ./gradlew :screens-core:test -PupdateGoldens and review the diff. */
class PersonDressedFixtureTest {
    @Test
    fun theSavedDocumentIsWhatTheCommandsMake() {
        val file = File(SAVED_FILE)
        val text = fennecText(personDressedDocument())
        if (System.getProperty(UPDATE_PROPERTY) != null) {
            file.parentFile.mkdirs()
            file.writeText(text)
            return
        }
        assertEquals(text, file.readText().replace("\r\n", "\n"))
        assertEquals(personDressedDocument(), savedPersonDressed())
        assertEquals(personDressedDocument(), SquashFormat().decode(file.readBytes()))
    }

    @Test
    fun summerWearsTheJacketOverTheShirtAndShowsTheToesInTheSandals() {
        val character = personDressedDocument().character
        assertEquals(setOf(SHIRT_GARMENT, SANDALS, JACKET), character.worn.map { it.id }.toSet())
        assertEquals(JACKET, character.worn.last().id, "the jacket is the outer layer")
        val foot = character.wornPart(assertNotNull(character.part(FOOT_R)))
        assertEquals(5f, foot.data.params["toes"], "the right foot shows its toes too")
        assertEquals(SANDALS, foot.changedBy)
    }
}
