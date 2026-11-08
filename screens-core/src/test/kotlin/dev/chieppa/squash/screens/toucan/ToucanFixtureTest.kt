package dev.chieppa.squash.screens.toucan

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.catalog.roles
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.isEye
import dev.chieppa.squash.document.isMouth
import dev.chieppa.squash.screens.fennec.fennecText
import dev.chieppa.squash.screens.shell.SquashFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SAVED_FILE = "../examples/$TOUCAN_RESOURCE"
private const val UPDATE_PROPERTY = "squash.updateGoldens"

/** The saved toucan stays what its commands make. Regenerate with ./gradlew :screens-core:test -PupdateGoldens and review the diff. */
class ToucanFixtureTest {
    private val shipped = TemplateFolder.shipped()

    @Test
    fun theSavedDocumentIsWhatTheCommandsMake() {
        val file = File(SAVED_FILE)
        val text = fennecText(toucanDocument())
        if (System.getProperty(UPDATE_PROPERTY) != null) {
            file.parentFile.mkdirs()
            file.writeText(text)
            return
        }
        assertEquals(text, file.readText().replace("\r\n", "\n"))
        assertEquals(toucanDocument(), savedToucan())
        assertEquals(toucanDocument(), SquashFormat().decode(file.readBytes()))
    }

    @Test
    fun everyRoleIsChosenAndTheBirdPerchesUpright() {
        val character = toucanDocument().character
        assertTrue(character.parts.none { it.roleGuessed })
        assertEquals(
            mapOf(Role.BODY to 1, Role.HEAD to 1, Role.JAW to 2, Role.WING to 2, Role.LEG to 2, Role.FOOT to 2, Role.TAIL to 1),
            character.parts.groupingBy { checkNotNull(it.role) }.eachCount(),
        )
        val roles = shipped.roles(character)
        assertTrue(roles.upright)
        assertTrue(!roles.fourLegged)
    }

    @Test
    fun theFaceHasTwoSideEyesAndAMouth() {
        val character = toucanDocument().character
        val head = character.resolved(assertNotNull(character.part(HEAD))).surface
        assertEquals(2, head.groups.count { it.isEye })
        assertEquals(1, head.groups.count { it.isMouth })
        val left = head.marks.first { it.id.value == "eye-l-lid" }.points.map { it.v }.average()
        val right = head.marks.first { it.id.value == "eye-r-lid" }.points.map { it.v }.average()
        assertEquals(0.26, left, 0.01, "the left eye sits about the landmark on the head's side")
        assertEquals(0.74, right, 0.01, "the right eye sits across the middle")
        assertEquals(setOf("eye-ring-l", "eye-ring-r"), head.regions.map { it.id.value }.toSet())
    }

    @Test
    fun itBuildsAndEveryPartLintsAsBuilt() {
        val document = toucanDocument()
        val library = TemplateLibrary(shipped.parts, shipped.plans)
        val plan = shipped.plans.first { it.ref == document.character.bodyPlan }
        val assembled = assembleNow(assemblyParts(document.character), { assertNotNull(document.character.part(it)).let(document.character::resolved) }, plan, library)
        assertTrue(assembled.parts.none { it.planned }, "the toucan fills every socket the plan offers")
        assembled.parts.forEach { assertEquals(emptyList(), TemplateLint.lint(it.template), "${it.id?.value} lints clean as built") }
        assertTrue(assembled.parts.all { it.surface.patches.isNotEmpty() })
        val feet = assembled.parts.filter { it.id == FOOT_L || it.id == FOOT_R }
        feet.forEach { assertEquals(assembled.floor, assertNotNull(it.box).low.y, 1e-4f, "${it.id?.value} stands on the ground") }
    }
}
