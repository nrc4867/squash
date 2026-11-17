package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.catalog.roles
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.isMouth
import dev.chieppa.squash.screens.fennec.fennecText
import dev.chieppa.squash.screens.shell.SquashFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SAVED_FILE = "../examples/$WOMAN_RESOURCE"
private const val UPDATE_PROPERTY = "squash.updateGoldens"

/** The saved woman stays what her commands make. Regenerate with ./gradlew :screens-core:test -PupdateGoldens and review the diff. */
class WomanFixtureTest {
    private val shipped = TemplateFolder.shipped()

    @Test
    fun theSavedDocumentIsWhatTheCommandsMake() {
        val file = File(SAVED_FILE)
        val text = fennecText(womanDocument())
        if (System.getProperty(UPDATE_PROPERTY) != null) {
            file.parentFile.mkdirs()
            file.writeText(text)
            return
        }
        assertEquals(text, file.readText().replace("\r\n", "\n"))
        assertEquals(womanDocument(), savedWoman())
        assertEquals(womanDocument(), SquashFormat().decode(file.readBytes()))
    }

    @Test
    fun everyRoleIsChosenOnTheBlankPlan() {
        val character = womanDocument().character
        assertTrue(character.parts.none { it.roleGuessed })
        assertEquals(
            mapOf(Role.BODY to 1, Role.NECK to 1, Role.HEAD to 1, Role.ARM to 2, Role.HAND to 2, Role.LEG to 2, Role.FOOT to 2, Role.HAIR to 8),
            character.parts.groupingBy { checkNotNull(it.role) }.eachCount(),
        )
        assertTrue(!shipped.roles(character).fourLegged)
    }

    @Test
    fun theFaceHasPaintedEyesAndAMouth() {
        val character = womanDocument().character
        val head = character.resolved(assertNotNull(character.part(HEAD))).surface
        val eye = character.paintedEyes.single()
        assertEquals(HEAD, eye.anchor.part)
        assertTrue(eye.mirrored && eye.look.lashes && eye.look.brow, "both eyes, with lashes and a brow")
        assertEquals(1, head.groups.count { it.isMouth })
    }

    @Test
    fun itBuildsAndEveryPartLintsAsBuilt() {
        val document = womanDocument()
        val library = TemplateLibrary(shipped.parts, shipped.plans)
        val plan = shipped.plans.first { it.ref == document.character.bodyPlan }
        val assembled = assembleNow(assemblyParts(document.character), { assertNotNull(document.character.part(it)).let(document.character::resolved) }, plan, library)
        assembled.parts.filter { !it.planned }.forEach { assertEquals(emptyList(), TemplateLint.lint(it.template), "${it.id?.value} lints clean as built") }
        assertTrue(assembled.parts.filter { !it.planned }.all { it.surface.patches.isNotEmpty() })
    }
}
