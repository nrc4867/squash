package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.JAW_JOINT
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.bendsOf
import dev.chieppa.squash.character.build.shownJoints
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.face.eyeLandmark
import dev.chieppa.squash.character.face.newEyesCommand
import dev.chieppa.squash.character.face.newMouthCommand
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetMarkControls
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.isEye
import dev.chieppa.squash.geom.bent
import dev.chieppa.squash.geom.surfaceAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val HEAD = PartId("head")
private val BEAK = PartId("beak")
private val LOWER = PartId("beak-lower")

/** How squarely a side eye must face out to its side, as its normal's share across the head. */
private const val SIDEWAYS = 0.7f

/** A bird head with a beak: the lower half swings with the head's jaw as the mouth opens, and Add eyes sets one eye on each side. */
class BirdHeadTest {
    private val plan = shipped.plans.single { it.id.value == "bird" }

    private fun bird(vararg more: (Character) -> DocumentCommand): Character {
        var next = 0
        val made = newCharacter(plan, shipped, "Bird") { "id-${next++}" }
        val torso = made.root
        val parts = listOf(
            AddParts(listOf(PartInstance(HEAD, template("head-bird").ref, SocketRef(torso, "neck"), role = Role.HEAD))),
            AddParts(listOf(PartInstance(BEAK, template("beak").ref, SocketRef(HEAD, "muzzle"), role = Role.JAW))),
            AddParts(listOf(PartInstance(LOWER, template("beak-lower").ref, SocketRef(BEAK, "lower"), role = Role.JAW))),
        )
        val built = (made.commands + parts).fold(made.document) { document, command -> command.apply(document).document }
        return more.fold(built) { document, command -> command(document.character).apply(document).document }.character
    }

    private fun opened(open: Float): Array<(Character) -> DocumentCommand> =
        arrayOf({ _ -> newMouthCommand(HEAD) }, { _ -> SetMarkControls(HEAD, MarkGroupId("mouth"), MarkControls(open = open)) })

    @Test
    fun theLowerBeakTakesTheHeadsJawAsTheMouthOpens() {
        val closed = bird(*opened(0f))
        val open = bird(*opened(1f))
        val lower = template("beak-lower")
        val head = template("head-bird")
        assertEquals(emptyMap(), shownJoints(closed, LOWER, lower))
        val jaw = assertNotNull(head.blocks.flatMap { it.joints() }.single { it.name == JAW_JOINT }.limits).max
        assertEquals(mapOf(JAW_JOINT to jaw), shownJoints(open, HEAD, head), "the head's jaw opens to its limit")
        assertEquals(mapOf(JAW_JOINT to jaw), shownJoints(open, LOWER, lower), "the lower beak takes the same angle")
        assertEquals(emptyMap(), shownJoints(open, BEAK, template("beak")), "the upper beak holds still")
    }

    @Test
    fun theLowerBeakSwingsDownWholeAboutItsBase() {
        val lower = template("beak-lower")
        val axes = PartBuilder.axes(lower)
        val rest = PartBuilder.mesh("beak-lower", lower, PartBuilder.build(lower))
        val swung = rest.bent(bendsOf(shownJoints(bird(*opened(1f)), LOWER, lower), lower, axes))
        val restTip = rest.patches.single().patch.surfaceAt(1f, 0.5f).position
        val swungTip = swung.patches.single().patch.surfaceAt(1f, 0.5f).position
        val restBase = rest.patches.single().patch.surfaceAt(0f, 0f).position
        val swungBase = swung.patches.single().patch.surfaceAt(0f, 0f).position
        assertTrue((swungTip - restTip) dot axes.up < 0f, "the tip drops")
        val top = rest.patches.single().patch.surfaceAt(0.6f, 0f).position
        val swungTop = swung.patches.single().patch.surfaceAt(0.6f, 0f).position
        assertTrue((swungTop - top) dot axes.up < 0f, "its top swings with it, the part turning whole")
        assertTrue((swungBase - restBase).length < (swungTip - restTip).length, "it turns about its base")
    }

    @Test
    fun addEyesSetsOneEyeOnEachSideOfTheHead() {
        val head = template("head-bird")
        val character = bird({ before -> assertNotNull(newEyesCommand(before, HEAD, eyeLandmark(head))) })
        val surface = character.resolved(assertNotNull(character.part(HEAD))).surface
        assertEquals(2, surface.groups.count { it.isEye })
        val patch = PartBuilder.build(head).single()
        val axes = PartBuilder.axes(head)
        val facing = listOf("eye-l-lid", "eye-r-lid").map { id ->
            val points = surface.marks.single { it.id.value == id }.points
            patch.surfaceAt(points.map { it.u }.average().toFloat(), points.map { it.v }.average().toFloat()).normal dot axes.side
        }
        assertTrue(facing.all { kotlin.math.abs(it) >= SIDEWAYS }, "each eye faces out to its side: $facing")
        assertTrue(facing[0] * facing[1] < 0f, "the two eyes face opposite sides")
    }

}
