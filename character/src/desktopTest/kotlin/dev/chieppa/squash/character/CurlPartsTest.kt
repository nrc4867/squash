package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.AxisCurveParams
import dev.chieppa.squash.character.build.AxisSpiral
import dev.chieppa.squash.character.build.CurveFit
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.build.sweeps
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.ringMiddle
import kotlin.math.acos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val STEPS = 200

/** A ring's size may differ from the drawing's by this share where the curve bends gently. */
private const val DRAWN_SIZE_SLACK = 0.05f

/** A hook turning back on itself: places that run out, up and back toward the root. */
private val HOOK = listOf(Vec3(0.4f, 0f, 0f), Vec3(0.7f, 0.25f, 0f), Vec3(0.55f, 0.5f, 0.1f), Vec3(0.3f, 0.45f, 0.2f))

/** Curves on every part that sweeps along its axis: places that turn back and leave the plane, spirals, drawn outlines along them. See HLD: surface, Curved axes. */
class CurlPartsTest {
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private fun latest(id: String): PartTemplate = assertNotNull(library.latest(TemplateId(id)))

    private fun centre(patch: Patch) = List(STEPS + 1) { patch.ringMiddle(it.toFloat() / STEPS) }

    private fun degrees(a: Vec3, b: Vec3): Float = Math.toDegrees(acos((a.normalized() dot b.normalized()).coerceIn(-1f, 1f)).toDouble()).toFloat()

    /** How far the centre line turns in all, in degrees, over its middle from [from] to [to] of its steps. */
    private fun turning(line: List<Vec3>, from: Float = 0.05f, to: Float = 0.9f): Float =
        line.subList((STEPS * from).toInt(), (STEPS * to).toInt()).zipWithNext { a, b -> b - a }.zipWithNext { a, b -> degrees(a, b) }.sum()

    /** A closed outline of straight sides from the socket end, [root] wide each way, to the tip, [tip] wide, [length] along. */
    private fun cone(length: Float, root: Float, tip: Float): OutlineCurve {
        val corners = listOf(Vec3(0f, root, 0f), Vec3(length, tip, 0f), Vec3(length, -tip, 0f), Vec3(0f, -root, 0f))
        val points = mutableListOf(CurvePoint(corners[0].x, corners[0].y))
        corners.zipWithNext().forEach { (a, b) ->
            for (t in listOf(1f / 3f, 2f / 3f, 1f)) (a + (b - a) * t).let { points += CurvePoint(it.x, it.y) }
        }
        return OutlineCurve(points)
    }

    private fun drawn(length: Float, root: Float, tip: Float) = cone(length, root, tip).let { mapOf(View.SIDE to it, View.TOP to it) }

    @Test
    fun everyPartThatSweepsTakesACurve() {
        val sweeping = setOf("tube", "horn", "spike", "antler", "tail", "tail-curve", "hair", "claw", "ear", "fin")
        for (id in sweeping) assertTrue(latest(id).sweeps, "$id sweeps along its axis")
        for (id in listOf("blob", "head-round", "hand", "paw", "foot", "ring")) assertFalse(latest(id).sweeps, "$id does not: a ring is a closed loop of its own")
    }

    @Test
    fun aHornsCurveTurnsBackOnItselfAndLeavesThePlane() {
        val horn = latest("horn")
        val patch = PartBuilder.build(horn, AxisCurveParams.of(HOOK)).first()
        val line = centre(patch)
        val axes = PartBuilder.axes(horn)
        assertTrue(turning(line) > 150f, "the horn hooks back: it turns ${turning(line)} degrees")
        assertTrue(line.maxOf { it dot axes.side } > 0.02f, "it leaves the plane toward its side")
        assertTrue((line.last() - line.first()).length < horn.axis.length * 0.8f, "its tip comes back toward its root")
        val mesh = PartBuilder.mesh("horn", horn, PartBuilder.build(horn, AxisCurveParams.of(HOOK)))
        for (part in mesh.patches) assertEquals(emptyList(), MeshInvariants.check(part, PartBuilder.openRings(horn, part.patch)), part.patch.name)
    }

    @Test
    fun aSpiralCoilsItsTurnsWithMoreRingsThanTheTemplate() {
        val horn = latest("horn")
        val params = AxisCurveParams.of(AxisSpiral(turns = 1.5f, pitch = 0.3f, shrink = 0.5f)) + mapOf("size" to 2f, "girth" to 0.06f)
        val patch = PartBuilder.build(horn, params).first()
        assertTrue(patch.nu > horn.topology.rings, "a spiral of 1.5 turns takes ${patch.nu} rings, more than the template's ${horn.topology.rings}")
        val turned = turning(centre(patch), 0.02f, 0.95f)
        assertTrue(turned > 1.5f * 360f * 0.8f, "the centre line turns $turned degrees over 1.5 turns")
        val side = PartBuilder.axes(horn).side
        assertTrue((centre(patch).last() - centre(patch).first()) dot side > 0.02f, "the pitch moves the coil toward the side")
        val fit = assertNotNull(CurveFit.of(horn, params))
        assertFalse(fit.ignored)
        assertFalse(fit.clipped, "a thin coil keeps its rings: ${fit.hold}")
    }

    @Test
    fun aDrawnPartFollowsItsCurveAtTheDrawingsWidth() {
        val horn = latest("horn")
        val outlines = drawn(0.3f, 0.03f, 0.01f)
        val straight = PartBuilder.build(horn, emptyMap(), outlines).first()
        val curved = PartBuilder.build(horn, AxisCurveParams.of(HOOK), outlines).first()
        assertTrue(turning(centre(curved)) > 150f, "the drawn horn follows its hook: ${turning(centre(curved))} degrees")
        for (u in listOf(0.2f, 0.4f, 0.6f)) {
            val want = assertNotNull(straight.slices).minBy { kotlin.math.abs(it.u - u) }
            val got = assertNotNull(curved.slices).minBy { kotlin.math.abs(it.u - u) }
            assertEquals(want.height, got.height, want.height * DRAWN_SIZE_SLACK + 1e-4f, "the ring at u $u keeps the drawing's height")
        }
        val reach = centre(curved).zipWithNext { a, b -> (b - a).length }.sum()
        assertEquals(0.3f, reach, 0.3f * 0.05f, "the drawn length is the length along the curve")
    }

    @Test
    fun aDrawnCurvedTailNoLongerIgnoresItsCurve() {
        val tail = latest("tail-curve")
        val outlines = drawn(0.6f, 0.05f, 0.02f)
        val plain = PartBuilder.build(tail, emptyMap(), outlines).first()
        val own = PartBuilder.build(tail, AxisCurveParams.of(HOOK), outlines).first()
        assertTrue(turning(centre(own)) > turning(centre(plain)) + 90f, "drawn, the tail follows its own curve")
    }

    @Test
    fun aCurveOnAPartThatDoesNotSweepIsIgnoredAndSaysSo() {
        val head = latest("head-round")
        val fit = assertNotNull(CurveFit.of(head, AxisCurveParams.of(HOOK)))
        assertTrue(fit.ignored, "a skull does not sweep")
        assertNull(CurveFit.of(latest("horn"), emptyMap()), "no curve of its own, nothing to say")
    }

    @Test
    fun aCurveTighterThanThePartIsThickIsClipped() {
        val horn = latest("horn")
        val tight = AxisCurveParams.of(AxisSpiral(turns = 3f, shrink = 0.3f)) + mapOf("girth" to 0.5f)
        val fit = assertNotNull(CurveFit.of(horn, tight))
        assertTrue(fit.clipped, "a fat horn in a tight coil narrows to ${fit.hold}")
    }

    private fun assembled(document: SquashDocument, planRef: BodyPlanRef): AssembledCharacter {
        val character = document.character
        return assembleNow(assemblyParts(character), { character.resolved(it) ?: PartData() }, library.plan(planRef), library)
    }

    @Test
    fun anAntlerOnAnAntlersTipSeatsInsideItsEnd() {
        val antler = latest("antler")
        assertEquals(2, antler.version)
        val planRef = BodyPlanRef(BodyPlanId("blank"), 1)
        val body = PartInstance(PartId("body"), TemplateRef(TemplateId("blob"), 1))
        val beam = PartInstance(PartId("beam"), TemplateRef(TemplateId("antler"), 2), SocketRef(body.id, HandSockets.name(1)))
        val fork = PartInstance(PartId("fork"), TemplateRef(TemplateId("antler"), 2), SocketRef(beam.id, "tip"))
        val empty = SquashDocument(Character(CharacterId("c"), "Stag", planRef))
        val socketed = HandSockets.add(body.id, 1, 0.6f, 0f, 0f, false).apply(AddParts(listOf(body)).apply(empty).document).document
        val character = assembled(AddParts(listOf(beam, fork)).apply(socketed).document, planRef)
        val parent = assertNotNull(character.part(beam.id))
        val child = assertNotNull(character.part(fork.id))
        val tip = assertNotNull(parent.middleAt(1f))
        val along = assertNotNull(parent.alongAt(1f))
        val back = (tip - child.origin) dot along
        assertTrue(back > 0f, "the fork's socket plane sits back inside the beam's end, $back along it")
        val off = ((tip - child.origin) - along * back).length
        assertTrue(off < 1e-3f, "and on the beam's own line, $off off it")
    }
}
