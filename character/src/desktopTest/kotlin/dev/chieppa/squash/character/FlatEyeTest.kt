package dev.chieppa.squash.character

import dev.chieppa.squash.character.face.eyeLandmark
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.EyeChartFrame
import dev.chieppa.squash.character.surface.EyeShapes
import dev.chieppa.squash.character.surface.FieldAnchors
import dev.chieppa.squash.character.surface.SurfaceEyes
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.geom.Vec3
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** How close two unit directions lie to count as the same. */
private const val SAME_WAY = 1e-3f

/** How much narrower than drawn, seen from the head's front, an eye following the head's curve reads at least. */
private const val NARROWER = 0.9f

/** How far round from the head's front, as a cosine, the eye sits: about 45 degrees, where a small head turns well away under it. */
private const val ROUND_FROM_FRONT = 0.7f

/** The steps round the head searched for that place. */
private const val ROUND_STEPS = 50

/**
 * A painted eye on a small round head: by default it faces the surface's normal at its middle and follows the head's curve, so seen from
 * the head's front it reads narrower than drawn; a flat eye faces the way the head faces and reads at its drawn width from there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlatEyeTest {
    private val headTemplate = shipped.parts.single { it.id.value == "head-round" && it.version == 2 }
    private val head = PartInstance(PartId("head"), TemplateRef(TemplateId("head-round"), 2))
    private val landmark = assertNotNull(eyeLandmark(headTemplate))

    /** A wide eye, so the round head turns away under it, with only its lid drawn. */
    private val look = EyeLook(width = 0.08f, height = 0.05f, lashes = 0, brow = false)

    /** The place at the eye landmark's u, round the head where its normal turns [ROUND_FROM_FRONT] from the head's facing. */
    private fun placeOn(surface: CharacterSurface): Anchor {
        val anchors = FieldAnchors(surface.field)
        val facing = headFacing(surface)
        return (0..ROUND_STEPS).map { Anchor(head.id, BODY_PATCH, landmark.u, it.toFloat() / ROUND_STEPS) }
            .mapNotNull { anchor -> anchors.resolve(anchor)?.let { anchor to abs((it.normal.normalized() dot facing) - ROUND_FROM_FRONT) } }
            .minBy { it.second }.first
    }

    private fun wrappedOn(surface: CharacterSurface) = PaintedEye("eyes", placeOn(surface), look, mirrored = false)

    private fun CharacterSession.surface(): CharacterSurface = assertNotNull(characterSurface.output.value?.surface)

    private fun headFacing(surface: CharacterSurface): Vec3 {
        val placed = surface.field.runs.first { it.partId == head.id }.part
        return (placed.turn * PartBuilder.facing(placed.template)).normalized()
    }

    private fun frame(surface: CharacterSurface, eye: PaintedEye): EyeChartFrame = SurfaceEyes.framesOf(surface, eye).single()

    /** The eye's lid line as built on [surface]. */
    private suspend fun lid(surface: CharacterSurface, eye: PaintedEye): List<Vec3> {
        val versions = SurfaceEyes.of(surface, listOf(eye)).eyes.single()
        return assertNotNull(versions.look.marks[head.id]).single { it.id == MarkId("${eye.id}-0-lid") }.points.map { it.position }
    }

    /** How far apart the two farthest of [points] lie, seen along [facing]. */
    private fun widthSeenAlong(points: List<Vec3>, facing: Vec3): Float {
        val seen = points.map { it - facing * (it dot facing) }
        return seen.maxOf { a -> seen.maxOf { b -> (a - b).length } }
    }

    @Test
    fun anEyeByDefaultFacesTheSurfacesNormalAndAFlatOneTheHeads() = runTest {
        val session = surfaceSession(head).also { it.settle() }
        val surface = session.surface()
        val wrapped = wrappedOn(surface)
        val flat = wrapped.copy(flat = true)
        val normal = assertNotNull(FieldAnchors(surface.field).resolve(wrapped.anchor)).normal.normalized()
        val facing = headFacing(surface)
        assertTrue((normal dot facing) < 1f - 0.05f, "the eye sits where the head turns away from its front: ${normal dot facing}")
        assertTrue((frame(surface, wrapped).faces.normalized() - normal).length < SAME_WAY, "a default eye faces the surface's normal")
        assertTrue((frame(surface, flat).faces.normalized() - facing).length < SAME_WAY, "a flat eye faces the head's facing")
    }

    @Test
    fun aFlatEyeReadsAtItsDrawnWidthFromTheHeadsFrontWhereADefaultOneFollowsTheCurve() = runTest {
        val session = surfaceSession(head).also { it.settle() }
        val surface = session.surface()
        val facing = headFacing(surface)
        val wrapped = wrappedOn(surface)
        val flat = wrapped.copy(flat = true)
        val flatFrame = frame(surface, flat)
        val drawn = assertNotNull(EyeShapes(look).lid).map { flatFrame.perU * it.u + flatFrame.perV * it.v }
        val drawnWidth = widthSeenAlong(drawn, facing)
        val cell = surface.field.cell
        val flatWidth = widthSeenAlong(lid(surface, flat), facing)
        val wrappedWidth = widthSeenAlong(lid(surface, wrapped), facing)
        assertEquals(drawnWidth, flatWidth, cell, "the flat eye's lid, seen from the head's front, is as wide as drawn; the default one reads $wrappedWidth")
        assertTrue(wrappedWidth < drawnWidth * NARROWER, "the default eye's lid follows the curve and reads narrower: $wrappedWidth against $drawnWidth")
    }
}
