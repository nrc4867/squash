package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.socketCreases
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.LineWeight
import dev.chieppa.squash.document.LineWeightId
import dev.chieppa.squash.document.SetLineWeight
import dev.chieppa.squash.geom.Vec3
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val WEIGHT = LineWeightId("flank")

/** How sharply a calm crease may turn between neighbouring points, as the cosine of the turn. */
private const val CALM_TURN = 0.3f

class BodyLinesTest {
    private val toViewer = Vec3(0f, 0.1f, -1f).normalized()

    @Test
    fun theLinesFollowTheGraphAndKeepEachPartsCreaseWithItsParent() = runTest {
        val session = surfaceSession(torsoPart, leftLeg, rightLeg)
        session.settle()
        val lines = assertNotNull(session.bodyLines.output.value)
        assertEquals(setOf(torsoPart.id, leftLeg.id, rightLeg.id), lines.parts.keys)
        assertTrue(lines.parts.getValue(leftLeg.id).crease.isNotEmpty(), "a leg meets the torso")
        assertTrue(lines.parts.getValue(torsoPart.id).crease.isEmpty(), "the torso has no parent")
        assertTrue(lines.parts.getValue(torsoPart.id).grids.isNotEmpty())
    }

    @Test
    fun aSurfaceEditFindsNoCreaseAgainAndWeighsOnlyThePartItNames() = runTest {
        val session = surfaceSession(torsoPart, leftLeg, rightLeg)
        session.settle()
        val before = assertNotNull(session.bodyLines.output.value)
        val weight = LineWeight(WEIGHT, listOf(Anchor(torsoPart.id, BODY_PATCH, 0.5f, 0.25f)), 0.4f, 1f)
        session.send(SetLineWeight(torsoPart.id, WEIGHT, weight))
        session.settle()
        val after = assertNotNull(session.bodyLines.output.value)
        assertSame(before.parts.getValue(leftLeg.id).crease.first().points, after.parts.getValue(leftLeg.id).crease.first().points)
        val torso = after.parts.getValue(torsoPart.id).grids.flatMap { it.contours { toViewer } }.flatMap { it.points }
        val leg = after.parts.getValue(leftLeg.id).grids.flatMap { it.contours { toViewer } }.flatMap { it.points }
        assertTrue(torso.maxOf { it.weight } > 1.5f, "the torso's contour crosses the weight")
        assertEquals(1f, leg.maxOf { it.weight }, "the leg beside it is not named")
    }

    @Test
    fun aCreaseRunsCalmlyRoundItsPart() = runTest {
        val session = surfaceSession(torsoPart, leftLeg, rightLeg)
        session.settle()
        val character = assertNotNull(session.assembly.output.value)
        val creases = socketCreases(character)
        assertTrue(creases.isNotEmpty())
        for (crease in creases) {
            for (run in crease.runs) {
                val steps = run.map { it.position }.zipWithNext { a, b -> b - a }.filter { it.length > 1e-5f }.map { it.normalized() }
                val sharpest = steps.zipWithNext { a, b -> a dot b }.minOrNull() ?: continue
                assertTrue(sharpest > CALM_TURN, "${crease.part.value}: a turn with cosine $sharpest")
            }
        }
    }
}
