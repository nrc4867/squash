package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.bendsOf
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.geom.AxisSpline
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.bent
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.ringMiddle
import dev.chieppa.squash.geom.surfaceAt
import kotlin.math.acos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TEMPLATE_ID = "tail-curve"
private const val STEPS = 160
private const val SHAPE_SAMPLES = 400

/** How near the centre line passes to each of the curve's places, as a share of the tail's length. */
private const val PLACE_REACH = 0.01f

/** The most the centre line or a line of the surface along u turns from one step of u to the next, in degrees. */
private const val SMOOTH_STEP_DEGREES = 3f

/** Each joint's turn in the posing test, in degrees. */
private const val JOINT_DEGREES = 15f

/** A tail whose rest shape is a smooth curve through a few places, built as a tube along it and bent by joints spread along it. */
class CurvedTailTest {
    private val template: PartTemplate = shipped.parts.single { it.id.value == TEMPLATE_ID && it.version == 1 }
    private val axes = PartBuilder.axes(template)
    private val patch: Patch = PartBuilder.build(template).first()
    private val length = template.axis.length

    private fun centre(of: Patch) = List(STEPS + 1) { of.ringMiddle(it.toFloat() / STEPS) }

    private fun degrees(a: Vec3, b: Vec3): Float = Math.toDegrees(acos((a.normalized() dot b.normalized()).coerceIn(-1f, 1f)).toDouble()).toFloat()

    private fun sharpestTurn(points: List<Vec3>): Float =
        points.zipWithNext { a, b -> b - a }.zipWithNext { a, b -> degrees(a, b) }.max()

    /** The steps of u along the tube, from past the rings that close onto the socket plane to where its round end begins. */
    private val tubeSteps = (STEPS / 20)..(STEPS * 4 / 5)

    private fun sharpestNormalJump(of: Patch): Float = listOf(0f, 0.25f, 0.5f, 0.75f).maxOf { v ->
        tubeSteps.maxOf { i -> degrees(of.surfaceAt(i.toFloat() / STEPS, v).normal, of.surfaceAt((i + 1f) / STEPS, v).normal) }
    }

    @Test
    fun theCentreLinePassesThroughItsPlacesAtTheTailsLength() {
        val values = template.defaults
        val places = listOf(Vec3.ZERO) + template.axis.curve.map { axes.point(it.along.resolve(values), it.up.resolve(values), it.side.resolve(values)) }
        val shape = AxisSpline(places)
        val shapeLength = (0 until SHAPE_SAMPLES).sumOf { (shape.pointAt((it + 1f) / SHAPE_SAMPLES) - shape.pointAt(it.toFloat() / SHAPE_SAMPLES)).length.toDouble() }.toFloat()
        val scale = length / shapeLength
        val line = centre(patch)
        for (place in places) {
            val nearest = line.minOf { (it - place * scale).length }
            assertTrue(nearest < PLACE_REACH * length, "the centre line passes $nearest from $place")
        }
        val reach = line.zipWithNext { a, b -> (b - a).length }.sum()
        assertEquals(length, reach, length * 0.02f, "the tail is its length along the curve")
    }

    @Test
    fun itsRestShapeIsACurveWithNoKinks() {
        val line = centre(patch)
        assertTrue(degrees(line[1] - line[0], axes.along) < 45f && (line.last() - line.first()).length < length * 0.98f, "the rest shape curves")
        assertTrue(sharpestTurn(line) < SMOOTH_STEP_DEGREES, "the centre line turns at most ${sharpestTurn(line)} degrees a step")
        assertTrue(sharpestNormalJump(patch) < SMOOTH_STEP_DEGREES, "the surface's normals turn at most ${sharpestNormalJump(patch)} degrees a step")
    }

    @Test
    fun itsMeshIsClosedAndClean() {
        val mesh = PartBuilder.mesh(TEMPLATE_ID, template, PartBuilder.build(template))
        for (part in mesh.patches) assertEquals(emptyList(), MeshInvariants.check(part, PartBuilder.openRings(template, part.patch)), part.patch.name)
    }

    @Test
    fun posingItsJointsBendsTheCurveSmoothly() {
        val mesh = PartBuilder.mesh(TEMPLATE_ID, template, PartBuilder.build(template))
        val joints = template.blocks.flatMap { it.joints() }.filter { it.name != "root" }.associate { it.name to JOINT_DEGREES }
        val posed = mesh.bent(bendsOf(joints, template, axes)).patches.first().patch
        val rest = mesh.patches.first().patch
        val tipMoved = (posed.ringMiddle(1f) - rest.ringMiddle(1f)).length
        assertTrue(tipMoved > length * 0.3f, "the tip swings $tipMoved")
        val restLine = centre(rest)
        val posedLine = centre(posed)
        val restReach = restLine.zipWithNext { a, b -> (b - a).length }.sum()
        val posedReach = posedLine.zipWithNext { a, b -> (b - a).length }.sum()
        assertEquals(restReach, posedReach, restReach * 0.03f, "bending keeps the tail's length")
        val ringUs = posed.slices!!.map { it.u }.filter { it * STEPS in tubeSteps.first.toFloat()..tubeSteps.last.toFloat() }
        val sharpest = listOf(0f, 0.25f, 0.5f, 0.75f).maxOf { v -> sharpestTurn(ringUs.map { posed.positionAt(it, v) }) }
        val sharpestAtRest = listOf(0f, 0.25f, 0.5f, 0.75f).maxOf { v -> sharpestTurn(ringUs.map { rest.positionAt(it, v) }) }
        assertTrue(sharpest < JOINT_DEGREES, "a line of the posed surface turns at most $sharpest degrees a ring, $sharpestAtRest at rest: no ring takes a whole joint's turn")
    }
}
