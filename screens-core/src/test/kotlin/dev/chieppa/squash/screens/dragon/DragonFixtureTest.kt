package dev.chieppa.squash.screens.dragon

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.screens.fennec.fennecText
import dev.chieppa.squash.screens.shell.SquashFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SAVED_FILE = "../examples/$DRAGON_RESOURCE"
private const val UPDATE_PROPERTY = "squash.updateGoldens"

/** The saved dragon stays what its commands make. Regenerate with ./gradlew :screens-core:test -PupdateGoldens and review the diff. */
class DragonFixtureTest {
    private val shipped = TemplateFolder.shipped()

    @Test
    fun theSavedDocumentIsWhatTheCommandsMake() {
        val file = File(SAVED_FILE)
        val text = fennecText(dragonDocument())
        if (System.getProperty(UPDATE_PROPERTY) != null) {
            file.parentFile.mkdirs()
            file.writeText(text)
            return
        }
        assertEquals(text, file.readText().replace("\r\n", "\n"))
        assertEquals(dragonDocument(), savedDragon())
        assertEquals(dragonDocument(), SquashFormat().decode(file.readBytes()))
    }

    @Test
    fun itsWingsAreMembraneWingsWebbedFingerToFingerAndToTheArm() {
        val document = dragonDocument()
        val character = document.character
        val wings = character.parts.filter { it.role == Role.WING }
        assertEquals(listOf(WING_L, WING_R), wings.map { it.id })
        assertTrue(wings.all { it.template.id == TemplateId("wing-membrane") })
        val library = TemplateLibrary(shipped.parts, shipped.plans)
        val plan = shipped.plans.first { it.ref == character.bodyPlan }
        val assembled = assembleNow(assemblyParts(character), { assertNotNull(character.part(it)).let(character::resolved) }, plan, library)
        for (wing in listOf(WING_L, WING_R)) {
            val names = assertNotNull(assembled.part(wing)).surface.patches.map { it.name }
            assertTrue(listOf("web:0", "web:1", "web:2", "skin:3").all { it in names }, "$wing: $names")
        }
    }
}
