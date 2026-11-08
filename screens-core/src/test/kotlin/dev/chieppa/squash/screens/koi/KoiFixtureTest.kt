package dev.chieppa.squash.screens.koi

import dev.chieppa.squash.document.Role
import dev.chieppa.squash.screens.fennec.fennecText
import dev.chieppa.squash.screens.shell.SquashFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SAVED_FILE = "../examples/$KOI_RESOURCE"
private const val UPDATE_PROPERTY = "squash.updateGoldens"

/** The saved koi stays what its commands make. Regenerate with ./gradlew :screens-core:test -PupdateGoldens and review the diff. */
class KoiFixtureTest {
    @Test
    fun theSavedDocumentIsWhatTheCommandsMake() {
        val file = File(SAVED_FILE)
        val text = fennecText(koiDocument())
        if (System.getProperty(UPDATE_PROPERTY) != null) {
            file.parentFile.mkdirs()
            file.writeText(text)
            return
        }
        assertEquals(text, file.readText().replace("\r\n", "\n"))
        assertEquals(koiDocument(), savedKoi())
    }

    @Test
    fun theExampleOpensAsASavedFile() {
        assertEquals(koiDocument(), SquashFormat().decode(File(SAVED_FILE).readBytes()))
    }

    @Test
    fun everyPartIsGenericWithItsRoleChosen() {
        val parts = koiDocument().character.parts
        assertTrue(parts.all { it.template.id.value in setOf("tube", "fin", "sheet", "blob") })
        assertTrue(parts.none { it.roleGuessed })
        assertEquals(
            mapOf(Role.BODY to 1, Role.FIN to 4, Role.TAIL to 1, Role.OTHER to 2),
            parts.groupingBy { checkNotNull(it.role) }.eachCount(),
        )
    }
}
