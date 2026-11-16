package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.catalog.roles
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.isEye
import dev.chieppa.squash.screens.fennec.fennecText
import dev.chieppa.squash.screens.shell.SquashFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SAVED_FILE = "../examples/$TWIN_RESOURCE"
private const val UPDATE_PROPERTY = "squash.updateGoldens"

/** The saved twin stays what her commands make. Regenerate with ./gradlew :screens-core:test -PupdateGoldens and review the diff. */
class TwinFixtureTest {
    private val shipped = TemplateFolder.shipped()

    @Test
    fun theSavedDocumentIsWhatTheCommandsMake() {
        val file = File(SAVED_FILE)
        val text = fennecText(twinDocument())
        if (System.getProperty(UPDATE_PROPERTY) != null) {
            file.parentFile.mkdirs()
            file.writeText(text)
            return
        }
        assertEquals(text, file.readText().replace("\r\n", "\n"))
        assertEquals(twinDocument(), savedTwin())
        assertEquals(twinDocument(), SquashFormat().decode(file.readBytes()))
    }

    @Test
    fun everyRoleIsChosenOnTheBlankPlan() {
        val character = twinDocument().character
        assertTrue(character.parts.none { it.roleGuessed })
        assertEquals(
            mapOf(Role.BODY to 1, Role.NECK to 1, Role.HEAD to 1, Role.ARM to 2, Role.HAND to 2, Role.LEG to 2, Role.FOOT to 2, Role.HAIR to 4),
            character.parts.groupingBy { checkNotNull(it.role) }.eachCount(),
        )
        assertTrue(!shipped.roles(character).fourLegged)
    }

    @Test
    fun theFaceHasPaintedEyesAndAMouth() {
        val character = twinDocument().character
        val head = character.resolved(assertNotNull(character.part(HEAD))).surface
        assertEquals(0, head.groups.count { it.isEye }, "a one-surface head's eyes are painted, not drawn")
        val eye = character.paintedEyes.single()
        assertEquals(HEAD, eye.anchor.part)
        assertTrue(eye.mirrored && eye.look.lashes && eye.look.brow, "both eyes, with lashes and a brow")
        assertEquals(1, head.groups.count { !it.isEye })
    }

    @Test
    fun sheIsBuiltAsOneSurfaceWithHerHairAsHairParts() {
        val character = twinDocument().character
        val hair = character.parts.filter { it.role == Role.HAIR }
        assertEquals(listOf("hair-cap", "hair", "hair", "hair"), hair.map { it.template.id.value })
    }

    @Test
    fun theDisplayPoseIsTheReferenceRestOverTheAPose() {
        val character = twinDocument().character
        assertEquals(TWIN_DISPLAY, character.displayPose)
        assertTrue(character.displayPose.bones.filter { it.joint == "base" }.all { it.lean == 9f - ARM_OUTWARD }, "each upper arm hangs 9 degrees out")
    }

    @Test
    fun theWalkTurnsTheHipsAndTheChestAgainstEachOtherAboutTheVertical() {
        val hips = assertNotNull(TWIN_WALK.of(TORSO, "base"))
        val chest = assertNotNull(TWIN_WALK.of(TORSO, "joint.1"))
        assertTrue(hips.turn > 0f && chest.turn < 0f, "the chest turns back against the hips")
        assertEquals(0f, hips.degrees + hips.lean + chest.degrees + chest.lean, "neither bends, only turns")
    }

    @Test
    fun itBuildsAndEveryPartLintsAsBuilt() {
        val document = twinDocument()
        val library = TemplateLibrary(shipped.parts, shipped.plans)
        val plan = shipped.plans.first { it.ref == document.character.bodyPlan }
        val assembled = assembleNow(assemblyParts(document.character), { assertNotNull(document.character.part(it)).let(document.character::resolved) }, plan, library)
        assembled.parts.filter { !it.planned }.forEach { assertEquals(emptyList(), TemplateLint.lint(it.template), "${it.id?.value} lints clean as built") }
        assertTrue(assembled.parts.filter { !it.planned }.all { it.surface.patches.isNotEmpty() })
    }
}
