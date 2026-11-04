package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.build.moveSocket
import dev.chieppa.squash.character.build.placedBy
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val CLOSE = 1e-3f

/** Rings keep a place on a joint, so moving one shifts them along the same silhouette, by no more than about three pixels for a part one unit long. */
private const val RINGS_RESPACE = 0.012f

/** The Move tool's values through the real build and assembly. See HLD: data-model, Placing parts. */
class MovePlacementTest {
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val planRef = BodyPlanRef(BodyPlanId("canine-quadruped"), 1)
    private fun ref(id: String) = TemplateRef(TemplateId(id), 1)
    private val torso = PartInstance(PartId("torso"), ref("torso"))
    private val legs = PairId("legs")
    private val left = PartInstance(PartId("leg-l"), ref("leg"), SocketRef(torso.id, "front-leg"), pair = legs, side = Side.LEFT)
    private val right = PartInstance(PartId("leg-r"), ref("leg"), SocketRef(torso.id, "front-leg"), pair = legs, side = Side.RIGHT)

    private val alone = SquashDocument(Character(CharacterId("c"), "Fennec", planRef))
        .let { AddParts(listOf(torso)).apply(it).document }
    private val dog = AddParts(listOf(left, right)).apply(alone).document

    private fun assembled(document: SquashDocument): AssembledCharacter {
        val character = document.character
        return assembleNow(assemblyParts(character), { character.resolved(it) ?: PartData() }, library.plan(planRef), library)
    }

    private fun SquashDocument.with(command: DocumentCommand) = command.apply(this).document

    private fun assertClose(expected: Float, actual: Float, what: String) = assertTrue(abs(expected - actual) < CLOSE, "$what: $expected and $actual")

    private fun AssembledCharacter.origin(part: PartInstance) = assertNotNull(part(part.id)).origin

    private fun AssembledCharacter.tip(part: PartInstance): Vec3 = assertNotNull(part(part.id)).let { assertNotNull(it.pointOn(1f, 0f)) }

    @Test
    fun outwardLeansBothLegsAwayFromTheBodysPlane() {
        val before = assembled(dog)
        val after = assembled(dog.with(SetParameter(left.id, PlacementParams.OUTWARD, 25f)))
        assertTrue(abs(after.tip(left).z) > abs(before.tip(left).z) + 0.05f, "the left leg's tip moves out")
        assertClose(after.tip(left).z, -after.tip(right).z, "the right mirrors the left")
        assertClose(before.origin(left).x, after.origin(left).x, "the leg stays on its socket")
    }

    @Test
    fun aRightSideOverrideLeansThatLegAlone() {
        val before = assembled(dog)
        val after = assembled(dog.with(SetParameter(right.id, PlacementParams.OUTWARD, 25f, thisSide = true)))
        assertClose(before.tip(left).z, after.tip(left).z, "the left stays")
        assertTrue(abs(after.tip(right).z) > abs(before.tip(right).z) + 0.05f, "the right moves out")
    }

    @Test
    fun aTurnedPartKeepsItsTopologyAndItsUp() {
        for (template in shipped.parts) {
            val rest = PartBuilder.axes(template)
            val turned = template.placedBy(mapOf(PlacementParams.OUTWARD to 50f, PlacementParams.LEAN to -50f))
            val axes = PartBuilder.axes(turned)
            assertTrue((axes.up dot rest.up) > 0.3f, "${template.id.value} keeps which way is up")
            assertEquals(template.ref, turned.ref)
            val plain = PartBuilder.build(template)
            val built = PartBuilder.build(turned)
            assertEquals(plain.map { it.nu to it.nv }, built.map { it.nu to it.nv }, template.id.value)
        }
    }

    @Test
    fun movingASocketCarriesBothSidesAndMirrorsTheirPlaces() {
        val before = assembled(dog)
        val after = assembled(dog.with(moveSocket(dog.character, torso.id, "front-leg", Side.LEFT, 0.6f, 0.35f, false, thisSide = false)))
        assertNotEquals(before.origin(left).x, after.origin(left).x)
        assertClose(after.origin(left).x, after.origin(right).x, "both sides sit at one place along the body")
        assertClose(after.origin(left).z, -after.origin(right).z, "and mirror across it")
    }

    @Test
    fun movingFromTheRightSideReadsTheSamePlaceAsTheLeft() {
        val fromLeft = assembled(dog.with(moveSocket(dog.character, torso.id, "front-leg", Side.LEFT, 0.6f, 0.35f, false, thisSide = false)))
        val fromRight = assembled(dog.with(moveSocket(dog.character, torso.id, "front-leg", Side.RIGHT, 0.6f, 0.65f, false, thisSide = false)))
        assertClose(fromLeft.origin(left).z, fromRight.origin(left).z, "left")
        assertClose(fromLeft.origin(right).z, fromRight.origin(right).z, "right")
    }

    @Test
    fun oneSideOfASingleParentsSocketMovesAloneWithMirrorOffAndIsClearedByABothSidesMove() {
        val before = assembled(dog)
        val apart = dog.with(moveSocket(dog.character, torso.id, "front-leg", Side.RIGHT, 0.6f, 0.65f, false, thisSide = true))
        assembled(apart).let {
            assertClose(before.origin(left).x, it.origin(left).x, "the left stays")
            assertNotEquals(before.origin(right).x, it.origin(right).x)
        }
        val together = assembled(apart.with(moveSocket(apart.character, torso.id, "front-leg", Side.LEFT, 0.5f, 0.35f, false, thisSide = false)))
        assertClose(together.origin(left).x, together.origin(right).x, "a move of both takes back the side's own place")
    }

    @Test
    fun undoingAMoveRestoresWhatWasThere() {
        val command = moveSocket(dog.character, torso.id, "front-leg", Side.LEFT, 0.6f, 0.35f, false, thisSide = false)
        val applied = command.apply(dog)
        assertEquals(dog, applied.undo.apply(applied.document).document)
    }

    @Test
    fun anEmptySocketMovesTheBodyPartItWouldTake() {
        val before = assembled(alone)
        val after = assembled(alone.with(moveSocket(alone.character, torso.id, "front-leg", null, 0.6f, 0.35f, false, thisSide = false)))
        val socket = { character: AssembledCharacter -> character.sockets.first { it.ref.socket == "front-leg" }.position }
        assertNotEquals(socket(before), socket(after))
        val planned = { character: AssembledCharacter -> character.parts.filter { it.planned }.map { it.origin } }
        assertNotEquals(planned(before), planned(after))
    }

    @Test
    fun aMovedKneeTakesAnUndrawnBendWithIt() {
        val leg = template("leg")
        val moved = leg.placedBy(mapOf(PlacementParams.joint("knee") to 0.3f))
        assertEquals(0.3f, moved.axis.bendAt)
        assertEquals(0.3f, moved.blocks.flatMap { it.joints() }.first { it.name == "knee" }.u)
        assertEquals(leg.blocks.flatMap { it.joints() }.first { it.name == "hip" }.u, moved.blocks.flatMap { it.joints() }.first { it.name == "hip" }.u)
    }

    @Test
    fun aDrawnPartKeepsItsShapeWhenItsKneeMoves() {
        val leg = template("leg")
        val side = outline(0f to 0.1f, 1f to 0.1f, 1f to -0.1f, 0f to -0.1f)
        val moved = leg.placedBy(mapOf(PlacementParams.joint("knee") to 0.3f))
        val outlines = mapOf(dev.chieppa.squash.document.View.SIDE to side)
        val before = PartBuilder.build(leg, outlines = outlines).single().vertices()
        val after = PartBuilder.build(moved, outlines = outlines).single().vertices()
        val furthest = before.indices.maxOf { (before[it] - after[it]).length }
        assertTrue(furthest < RINGS_RESPACE, "the mesh moves by $furthest")
    }
}
