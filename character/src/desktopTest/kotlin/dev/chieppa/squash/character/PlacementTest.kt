package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private const val CLOSE = 1e-3f

@OptIn(ExperimentalCoroutinesApi::class)
class PlacementTest {
    private fun ref(id: String) = TemplateRef(TemplateId(id), 1)
    private val torso = PartInstance(PartId("torso"), ref("torso"))
    private val legs = PairId("legs")
    private val paws = PairId("paws")
    private val ears = PairId("ears")
    private val leftLeg = PartInstance(PartId("leg-l"), ref("leg"), SocketRef(torso.id, "front-leg"), pair = legs, side = Side.LEFT)
    private val rightLeg = PartInstance(PartId("leg-r"), ref("leg"), SocketRef(torso.id, "front-leg"), pair = legs, side = Side.RIGHT)
    private val leftPaw = PartInstance(PartId("paw-l"), ref("paw"), SocketRef(leftLeg.id, "paw"), pair = paws, side = Side.LEFT)
    private val rightPaw = PartInstance(PartId("paw-r"), ref("paw"), SocketRef(rightLeg.id, "paw"), pair = paws, side = Side.RIGHT)
    private val body = outline(0f to 0.3f, 1.2f to 0.3f, 1.2f to -0.3f, 0f to -0.3f)
    private val drawnLeg = outline(0f to 0.2f, 0.4f to 0.1f, 0.4f to -0.1f, 0f to -0.2f)

    private fun TestScope.session(vararg parts: PartInstance): CharacterSession {
        val empty = SquashDocument(Character(CharacterId("dog"), "Dog", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))
        val session = CharacterSession(
            AddParts(parts.toList()).apply(empty).document,
            TemplateLibrary(shipped.parts, shipped.plans),
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        runCurrent()
        return session
    }

    private fun CharacterSession.character(): AssembledCharacter {
        assertTrue(graph.settled.value)
        return assertNotNull(assembly.output.value)
    }

    private fun AssembledCharacter.of(part: PartInstance) = assertNotNull(part(part.id))

    private fun AssembledCharacter.planned(template: String, side: Side?): PlacedPart =
        parts.first { it.planned && it.template.id.value == template && it.side == side }

    private fun assertNear(expected: Vec3, actual: Vec3, message: String = "") =
        assertTrue((expected - actual).length < CLOSE, "$message expected $expected, got $actual")

    @Test
    fun theRootSitsAtTheOriginAlongThePlansRest() = runTest {
        val placed = session(torso).character().of(torso)
        assertEquals(Vec3.ZERO, placed.origin)
        assertFalse(placed.mirrored)
    }

    @Test
    fun aSocketPlaneSitsOnItsParentsSurfaceSunkAlongItsOwnAxis() = runTest {
        val character = session(torso, leftLeg, rightLeg).character()
        val parent = character.of(torso)
        val leg = character.of(leftLeg)
        val spec = shipped.plans.first().sockets.first { it.name == "front-leg" }
        val onSurface = parent.surface.patches.first().positionAt(spec.u, spec.v)
        val along = PartBuilder.axes(leg.template).along
        val sunk = onSurface - leg.origin
        assertTrue((sunk dot along) > 0f, "the leg's open end sits inside the body: $sunk")
        assertEquals(sunk.length, abs(sunk dot along), CLOSE, "straight back along the leg: $sunk")
    }

    @Test
    fun aSinkInsideItsParentHidesTheOpenRing() = runTest {
        val character = session(torso, leftLeg, rightLeg).character()
        val parent = assertNotNull(character.of(torso).box)
        val origin = character.of(leftLeg).origin
        assertTrue(origin.y > parent.low.y && origin.y < parent.high.y, "the leg's plane is within the body's height: $origin")
    }

    @Test
    fun aRightSideMirrorsItsLeftAcrossTheBodysPlane() = runTest {
        val character = session(torso, leftLeg, rightLeg).character()
        val left = character.of(leftLeg)
        val right = character.of(rightLeg)
        assertTrue(abs(left.origin.z) > CLOSE, "a leg sits off the body's middle")
        assertNear(left.origin.mirrored, right.origin)
        assertTrue(right.mirrored && !left.mirrored)
        val leftMesh = left.surface.mesh
        val rightMesh = right.surface.mesh
        for (v in 0 until leftMesh.vertexCount) assertNear(leftMesh.position(v).mirrored, rightMesh.position(v))
    }

    @Test
    fun aMirroredMeshStillFacesOut() {
        val leg = shipped.parts.first { it.id.value == "leg" }
        val own = RenderMesh.of(PartBuilder.mesh("leg", leg, PartBuilder.build(leg)))
        val mirrored = own.mirrored()
        var faces = 0
        for (t in 0 until mirrored.indices.size / 3) {
            val a = mirrored.position(mirrored.indices[t * 3])
            val b = mirrored.position(mirrored.indices[t * 3 + 1])
            val c = mirrored.position(mirrored.indices[t * 3 + 2])
            val face = (b - a) cross (c - a)
            if (face.length < 1e-9f) continue
            val at = mirrored.indices[t * 3] * RenderMesh.FLOATS_PER_VERTEX + RenderMesh.NORMAL_OFFSET_FLOATS
            val stored = Vec3(mirrored.vertices[at], mirrored.vertices[at + 1], mirrored.vertices[at + 2])
            assertTrue((face dot stored) > 0f, "triangle $t winds against its normal")
            faces++
        }
        assertTrue(faces > 0)
        val center = (0 until mirrored.vertexCount).map { mirrored.position(it) }.reduce(Vec3::plus) * (1f / mirrored.vertexCount)
        val first = mirrored.position(mirrored.indices[0])
        val normal = Vec3(mirrored.vertices[mirrored.indices[0] * 6 + 3], mirrored.vertices[mirrored.indices[0] * 6 + 4], mirrored.vertices[mirrored.indices[0] * 6 + 5])
        assertTrue((normal dot (first - center)) > 0f, "and that normal points out")
    }

    @Test
    fun aPairedChildSitsOnItsOwnSideOfAPairedParent() = runTest {
        val character = session(torso, leftLeg, rightLeg, leftPaw, rightPaw).character()
        val left = character.of(leftPaw)
        val right = character.of(rightPaw)
        assertNear(left.origin.mirrored, right.origin)
        assertTrue(left.origin.z * character.of(leftLeg).origin.z > 0f, "the left paw is on the left leg's side")
        assertTrue((left.origin - character.of(leftLeg).origin).length < 1f)
    }

    @Test
    fun aRightChildOnASingleParentReadsTheMirroredSocket() = runTest {
        val head = PartInstance(PartId("head"), ref("head"), SocketRef(torso.id, "neck"))
        val leftEar = PartInstance(PartId("ear-l"), ref("ear"), SocketRef(head.id, "ear"), pair = ears, side = Side.LEFT)
        val rightEar = PartInstance(PartId("ear-r"), ref("ear"), SocketRef(head.id, "ear"), pair = ears, side = Side.RIGHT)
        val character = session(torso, head, leftEar, rightEar).character()
        val spec = shipped.parts.first { it.id.value == "head" }.blocks.flatMap { it.sockets() }.single()
        val headPlaced = character.of(head)
        val leftPoint = assertNotNull(headPlaced.pointOn(spec.u, spec.v))
        val rightPoint = assertNotNull(headPlaced.pointOn(spec.u, 1f - spec.v))
        assertTrue(leftPoint.z < 0f && rightPoint.z > 0f, "left is the near side in Side: $leftPoint, $rightPoint")
        assertTrue(character.of(leftEar).origin.z < 0f && character.of(rightEar).origin.z > 0f)
    }

    @Test
    fun aChildMovesWhenItsParentIsRedrawnAndUndoPutsItBack() = runTest {
        val session = session(torso, leftLeg, rightLeg, leftPaw, rightPaw)
        val before = session.character()
        val legBefore = before.of(leftLeg).origin
        val pawBefore = before.of(leftPaw).origin

        session.send(SetOutline(torso.id, View.SIDE, body))
        assertFalse(session.graph.settled.value, "the whole character waits on the redraw")
        runCurrent()
        val after = session.character()
        assertNotEquals(legBefore, after.of(leftLeg).origin)
        assertNotEquals(pawBefore, after.of(leftPaw).origin, "and a grandchild with it")
        assertNear(after.of(leftLeg).origin.mirrored, after.of(rightLeg).origin)

        session.history.undo()
        runCurrent()
        assertSame(before, session.character(), "undo reads the cache")
    }

    @Test
    fun drawingAPartLeavesItsOwnSocketPlaneWhereItWas() = runTest {
        val session = session(torso, leftLeg, rightLeg)
        val before = session.character().of(leftLeg).origin
        session.send(SetOutline(leftLeg.id, View.SIDE, drawnLeg))
        runCurrent()
        assertEquals(before, session.character().of(leftLeg).origin)
    }

    @Test
    fun aMeshOfTheSelectedPartIsNotRederivedWhenItsParentMoves() = runTest {
        val session = session(torso, leftLeg, rightLeg)
        val before = session.character().of(leftLeg).surface
        session.send(SetOutline(torso.id, View.SIDE, body))
        runCurrent()
        assertSame(before, session.character().of(leftLeg).surface, "the GPU keeps its buffers")
    }

    @Test
    fun emptySocketsShowAsPlannedPartsAndAsPlaces() = runTest {
        val character = session(torso).character()
        val planned = character.parts.filter { it.planned }.map { it.template.id.value }.groupingBy { it }.eachCount()
        assertEquals(mapOf("head" to 1, "ear" to 2, "leg" to 4, "paw" to 4, "tail" to 1), planned)
        assertEquals(setOf("neck", "front-leg", "back-leg", "tail"), character.sockets.map { it.ref.socket }.toSet())
        assertTrue(character.sockets.all { it.ref.parent == torso.id })
        val paw = character.planned("paw", Side.RIGHT)
        assertTrue(paw.mirrored && paw.origin.z > 0f, "a planned right paw sits on a planned right leg")
    }

    @Test
    fun aFilledSocketStopsBeingAPlace() = runTest {
        val character = session(torso, leftLeg, rightLeg).character()
        val names = character.sockets.map { it.ref }
        assertFalse(SocketRef(torso.id, "front-leg") in names)
        assertTrue(SocketRef(leftLeg.id, "paw") in names, "the leader's own socket opens")
        assertFalse(SocketRef(rightLeg.id, "paw") in names, "a pair is reached through its left side")
    }

    @Test
    fun aPartOnASocketNoOneOffersOrAGoneParentStillShows() = runTest {
        val lost = PartInstance(PartId("lost"), ref("tail"), SocketRef(torso.id, "no-such"))
        val orphan = PartInstance(PartId("orphan"), ref("tail"), SocketRef(PartId("gone"), "tail"))
        val character = session(torso, lost, orphan).character()
        assertEquals(Vec3.ZERO, character.of(lost).origin)
        assertEquals(Vec3.ZERO, character.of(orphan).origin)
    }

    @Test
    fun theUndrawnDogStandsSquareWithItsHeadForwardAndItsFeetOnTheGround() = runTest {
        val character = session(torso).character()
        val body = assertNotNull(character.part(torso.id)?.box)
        val head = assertNotNull(character.planned("head", null).box)
        val tail = assertNotNull(character.planned("tail", null).box)
        assertTrue(head.center.x > body.high.x * 0.8f && head.high.x > body.high.x, "the head goes forward: $head")
        assertTrue(head.center.y >= body.center.y, "and up")
        assertTrue(tail.center.x < body.low.x + 0.1f && tail.high.y > body.high.y, "the tail goes back and up: $tail")
        val paws = character.parts.filter { it.template.id.value == "paw" }.map { assertNotNull(it.box) }
        val floor = character.floor
        assertEquals(floor, paws.minOf { it.low.y }, CLOSE, "the ground sits under the lowest vertex")
        assertTrue(paws.all { abs(it.low.y - floor) < CLOSE }, "every foot is on it")
        val legs = character.parts.filter { it.template.id.value == "leg" }.map { assertNotNull(it.box) }
        assertTrue(legs.all { it.size.y > it.size.x * 2f }, "legs stand tall")
        assertTrue(legs.all { it.low.y < body.low.y }, "and reach below the body")
    }

    @Test
    fun thePlanAddsNothingOnceEverySocketIsFilled() = runTest {
        val character = session(torso, leftLeg, rightLeg, leftPaw, rightPaw).character()
        assertEquals(0, character.parts.count { it.planned && it.template.id.value == "paw" && it.origin == character.of(leftPaw).origin })
    }

    private fun Vec3.isFinite() = x.isFinite() && y.isFinite() && z.isFinite()

    @Test
    fun everySocketOfTheShippedPlanPlacesFinitely() = runTest {
        session(torso).character().parts.forEach { assertTrue(it.origin.isFinite(), "${it.template.id}: ${it.origin}") }
    }
}
