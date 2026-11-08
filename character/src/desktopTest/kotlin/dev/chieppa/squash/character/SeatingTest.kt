package dev.chieppa.squash.character

import dev.chieppa.squash.character.block.ToeRow
import dev.chieppa.squash.character.block.Tube
import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.lint.SocketFit
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.MountSpec
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val CLOSE = 1e-3f

/** How far from the torso's axis the head's mount may land: the top ring's normal is read a cell off the pole. */
private const val OVER_THE_AXIS = 0.02f
private val TORSO = PartId("torso")
private val HEAD = PartId("head")
private val LEG = PartId("leg")

/** How a part seats on its socket: a closed part carried by its mount, a socket's own depth, and the lint that both keep it inside. */
class SeatingTest {
    private val plan = shipped.plans.single { it.id.value == "biped" }
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val torso = template("torso-upright")
    private val head = template("head-round")
    private val leg = template("leg")

    private fun assembled(vararg parts: AssemblyPart) = assembleNow(parts.toList(), { PartData() }, plan, library)

    @Test
    fun aMountedHeadHangsItsMountOnTheNeckNotItsAxisStart() {
        val mount = assertNotNull(head.mount)
        val body = assembled(AssemblyPart(TORSO, torso.ref, null, null, null), AssemblyPart(HEAD, head.ref, SocketRef(TORSO, "neck"), null, null))
        val placed = assertNotNull(body.part(HEAD))
        val hung = assertNotNull(placed.pointOn(mount.u, mount.v))
        assertTrue(abs(hung.x) < OVER_THE_AXIS && abs(hung.z) < OVER_THE_AXIS, "the mount sits over the torso's axis, at $hung")
        assertTrue(placed.origin.x < -CLOSE, "the back of the skull lies behind the neck")
        assertEquals(mount.u, head.blocks.flatMap { it.joints() }.first { it.name == "neck" }.u, "the head turns about where it hangs")
    }

    @Test
    fun aSocketsSinkMultipliesHowDeepItsPartSits() {
        val hip = plan.sockets.single { it.name == "hip" }
        val shallow = plan.copy(sockets = plan.sockets.map { if (it.name == "hip") it.copy(sink = 1f) else it })
        val shallowLibrary = TemplateLibrary(shipped.parts, listOf(shallow))
        val parts = listOf(AssemblyPart(TORSO, torso.ref, null, null, null), AssemblyPart(LEG, leg.ref, SocketRef(TORSO, "hip"), null, Side.LEFT))
        val deep = assertNotNull(assembleNow(parts, { PartData() }, plan, library).part(LEG)).origin
        val plain = assertNotNull(assembleNow(parts, { PartData() }, shallow, shallowLibrary).part(LEG)).origin
        assertEquals(library.socketSink(leg.ref) * (hip.sink - 1f), (deep - plain).length, CLOSE)
    }

    @Test
    fun theFitLintReadsTheSinkAndTheShippedPlansPass() {
        assertEquals(true, SocketFit.encloses(torso, "hip", leg, plan))
        val shallow = plan.copy(sockets = plan.sockets.map { if (it.name == "hip") it.copy(sink = 0.6f) else it })
        assertEquals(false, SocketFit.encloses(torso, "hip", leg, shallow), "at the template's own depth the leg's open ring clears the seat")
    }

    @Test
    fun aMountLintsOnlyOnAClosedPartAndOnItsSurface() {
        assertEquals(emptyList(), TemplateLint.lint(head))
        assertEquals(listOf("$.mount.u"), TemplateLint.lint(leg.copy(mount = MountSpec(0.5f, 0.5f))).map { it.path })
        assertEquals(listOf("$.mount.v"), TemplateLint.lint(head.copy(mount = MountSpec(0.5f, 1.5f))).map { it.path })
    }

    @Test
    fun aDigitFlaresAtItsRootAndNowhereElse() {
        val hand = template("hand")
        val row = hand.blocks.filterIsInstance<ToeRow>().single()
        val flared = finger(hand)
        val plain = finger(hand.copy(blocks = hand.blocks.map { if (it is ToeRow) it.copy(flare = Param.of(0f)) else it }))
        assertTrue(row.flare.resolve(emptyMap()) > 0f)
        val root = flared.size / 3
        assertTrue(flared.take(root).maxOf { it.height } > plain.take(root).maxOf { it.height }, "the root is taller")
        assertEquals(plain.last().height, flared.last().height, "the tip is not")
        assertEquals(plain[plain.size / 2].height, flared[flared.size / 2].height, "nor the middle")
    }

    @Test
    fun aTubesWidthFlattensAnUndrawnRun() {
        val paw = template("paw")
        val run = paw.blocks.filterIsInstance<Tube>().first()
        val ring = assertNotNull(PartBuilder.build(paw).first { it.name == "body" }.slices)[paw.topology.rings / 3]
        assertEquals(run.width.resolve(emptyMap()), ring.width / ring.height, CLOSE)
    }

    private fun finger(hand: PartTemplate) =
        assertNotNull(PartBuilder.build(hand).first { it.name == "fingers:2" }.slices)
}
