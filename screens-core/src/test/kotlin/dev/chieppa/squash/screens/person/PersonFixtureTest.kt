package dev.chieppa.squash.screens.person

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.catalog.roles
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.isEye
import dev.chieppa.squash.screens.fennec.fennecText
import dev.chieppa.squash.screens.shell.SquashFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SAVED_FILE = "../examples/$PERSON_RESOURCE"
private const val UPDATE_PROPERTY = "squash.updateGoldens"

/** The saved person stays what its commands make. Regenerate with ./gradlew :screens-core:test -PupdateGoldens and review the diff. */
class PersonFixtureTest {
    private val shipped = TemplateFolder.shipped()

    @Test
    fun theSavedDocumentIsWhatTheCommandsMake() {
        val file = File(SAVED_FILE)
        val text = fennecText(personDocument())
        if (System.getProperty(UPDATE_PROPERTY) != null) {
            file.parentFile.mkdirs()
            file.writeText(text)
            return
        }
        assertEquals(text, file.readText().replace("\r\n", "\n"))
        assertEquals(personDocument(), savedPerson())
        assertEquals(personDocument(), SquashFormat().decode(file.readBytes()))
    }

    @Test
    fun everyRoleIsChosenAndTheCharacterStandsUpright() {
        val character = personDocument().character
        assertTrue(character.parts.none { it.roleGuessed })
        assertEquals(
            mapOf(Role.BODY to 1, Role.HEAD to 1, Role.ARM to 2, Role.HAND to 2, Role.LEG to 2, Role.FOOT to 2, Role.OTHER to 1),
            character.parts.groupingBy { checkNotNull(it.role) }.eachCount(),
        )
        val roles = shipped.roles(character)
        assertTrue(roles.upright)
        assertTrue(!roles.fourLegged)
        assertEquals(Role.EAR, roles.guess(assertNotNull(character.part(HAIR))), "the hair on the crown is guessed an ear until chosen")
    }

    @Test
    fun theFaceHasTwoEyesAboutTheRoundHeadsLandmarkAndAMouth() {
        val character = personDocument().character
        val head = character.resolved(assertNotNull(character.part(HEAD))).surface
        assertEquals(2, head.groups.count { it.isEye })
        val mouth = head.groups.single { !it.isEye }
        assertTrue(mouth.roles.single { it.role == MarkRole.LIP_LINE }.marks.isNotEmpty())
        val lid = head.marks.first { it.id.value == "eye-l-lid" }
        assertEquals(0.9f, lid.points.map { it.u }.average().toFloat(), 0.01f, "the left eye sits about the landmark's u")
    }

    @Test
    fun itBuildsAndEveryPartLintsAsBuilt() {
        val document = personDocument()
        val library = TemplateLibrary(shipped.parts, shipped.plans)
        val plan = shipped.plans.first { it.ref == document.character.bodyPlan }
        val assembled = assembleNow(assemblyParts(document.character), { assertNotNull(document.character.part(it)).let(document.character::resolved) }, plan, library)
        assembled.parts.filter { !it.planned }.forEach { assertEquals(emptyList(), TemplateLint.lint(it.template), "${it.id?.value} lints clean as built") }
        assertTrue(assembled.parts.filter { !it.planned }.all { it.surface.patches.isNotEmpty() })
    }
}
