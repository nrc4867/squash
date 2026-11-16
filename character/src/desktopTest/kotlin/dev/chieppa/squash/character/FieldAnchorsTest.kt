package dev.chieppa.squash.character

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.FieldAnchors
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.surface.faceMap
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.geom.Vec3
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Anchors near a join resolved on a one surface's field: each lands on the surface, on its own part's side of the join, near where its
 * part's own surface holds it, and stays put on its part when the neighbouring part is made larger.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FieldAnchorsTest {
    /** Near the leg's root, where the join to the torso blends it; and on the torso's own surface beside the leg. */
    private val onLeg = Anchor(leftLeg.id, BODY_PATCH, 0.06f, 0.3f)
    private val onTorso = Anchor(torsoPart.id, BODY_PATCH, 0.8f, 0.7f)

    private fun CharacterSession.surface(): CharacterSurface = assertNotNull(characterSurface.output.value?.surface)

    private fun CharacterSession.origin(part: PartId): Vec3 = assertNotNull(placed.output.value?.part(part)).origin

    @Test
    fun anchorsNearAJoinLandOnTheSurfaceOnTheirOwnPartsSide() = runTest {
        val session = surfaceSession(torsoPart, leftLeg).also {
            it.settle()
        }
        val surface = session.surface()
        val anchors = FieldAnchors(surface.field)
        for (anchor in listOf(onLeg, onTorso)) {
            val point = assertNotNull(anchors.resolve(anchor))
            assertEquals(0f, surface.field.field.value(point.position), surface.field.cell * 0.01f, "$anchor lands on the surface")
            val runs = surface.field.runs
            val owns = runs.indices.associateWith { surface.field.field.members[it].own(point.position) }
            val own = owns.filterKeys { runs[it].partId == anchor.part }.values.min()
            val other = owns.filterKeys { runs[it].partId != anchor.part }.values.min()
            assertTrue(own <= other, "$anchor lies on its own part's side of the join: $own against $other")
        }
    }

    @Test
    fun anAnchorOnTheLegStaysOnItWhenTheTorsoGrows() = runTest {
        val session = surfaceSession(torsoPart, leftLeg).also {
            it.settle()
        }
        val before = assertNotNull(FieldAnchors(session.surface().field).resolve(onLeg)).position - session.origin(leftLeg.id)
        session.send(SetParameter(torsoPart.id, "size", 1.02f))
        session.settle()
        val after = assertNotNull(FieldAnchors(session.surface().field).resolve(onLeg)).position - session.origin(leftLeg.id)
        val cell = session.surface().field.cell
        assertTrue((after - before).length < cell, "the leg's anchor moves ${(after - before).length} on its leg, under a cell of $cell")
        assertTrue(abs(after.length - before.length) < cell)
    }

    @Test
    fun aPartsSurfaceWorkLandsOnTheSurfaceInBodySpace() = runTest {
        val session = surfaceSession(torsoPart, leftLeg).also {
            it.send(SetMark(torsoPart.id, MarkId("stripe"), Mark(MarkId("stripe"), listOf(onTorso, onTorso.copy(u = 0.7f), Anchor(leftLeg.id, BODY_PATCH, 0.1f, 0.3f)))))
            it.settle()
        }
        val surface = session.surface()
        val points = assertNotNull(session.bodySurfaceWork.output.value?.parts?.get(torsoPart.id)).marks.first { it.id == MarkId("stripe") }.points
        assertEquals(3, points.size)
        for (point in points) {
            assertEquals(0f, surface.field.field.value(point.position), surface.field.cell * 0.01f, "${point.anchor} lands on the surface")
            assertEquals(assertNotNull(FieldAnchors(surface.field).resolve(point.anchor)).position, point.position, "${point.anchor} lands where the field puts it")
        }
    }

    @Test
    fun furRootedOnTheOneSurfaceStandsOnIt() = runTest {
        val tuft = MarkId("tuft")
        val session = surfaceSession(torsoPart, leftLeg).also {
            it.send(SetMark(torsoPart.id, tuft, Mark(tuft, listOf(onTorso), kind = MarkKind.FUR)))
            it.settle()
        }
        val surface = session.surface()
        val root = assertNotNull(session.bodyFur.output.value?.parts?.get(torsoPart.id)).roots.single { it.id == tuft }
        assertEquals(0f, surface.field.field.value(root.position), surface.field.cell * 0.01f, "the tuft's root lies on the surface")
    }

    @Test
    fun aFaceMapOnTheOneSurfaceLandsWhereARayFromAheadMeetsIt() = runTest {
        val head = PartInstance(PartId("head"), templateRef("head"), SocketRef(torsoPart.id, "neck"))
        val session = surfaceSession(torsoPart, head).also {
            it.settle()
        }
        val surface = session.surface()
        val face = assertNotNull(surface.faceMap(head.id, 1f))
        val placed = assertNotNull(session.placed.output.value?.part(head.id))
        val ahead = placed.surface.shown(PartBuilder.facing(placed.template)).normalized()
        val up = (Vec3.Y - ahead * (Vec3.Y dot ahead)).normalized()
        val left = up cross ahead
        val body = surface.field.runs.indexOfFirst { it.partId == head.id && it.patch.name == BODY_PATCH }
        val tip = (0 until surface.mesh.vertexCount).filter { surface.mesh.winners[it] == body }.maxBy { surface.mesh.position(it) dot ahead }
        val front = surface.mesh.position(tip) - placed.origin
        for ((dx, dy) in listOf(0f to 0f, 0.02f to 0.02f, -0.03f to 0.01f)) {
            val x = (front dot left) + dx
            val y = (front dot up) + dy
            val anchor = assertNotNull(face.at(x, y), "($x, $y) lies on the face")
            val point = assertNotNull(FieldAnchors(surface.field).resolve(anchor)).position - placed.origin
            val cell = surface.field.cell
            assertEquals(x, point dot left, cell, "($x, $y) across")
            assertEquals(y, point dot up, cell, "($x, $y) up")
        }
    }
}
