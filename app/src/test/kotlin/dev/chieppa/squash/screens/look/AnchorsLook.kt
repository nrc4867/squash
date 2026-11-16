package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.geom.surface.HELD
import dev.chieppa.squash.screens.TestWork
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How far round a join its anchors' spot reaches, in body units. */
private const val SPOT_RADIUS = 0.16f

/** How much of the surface a part holds where its ring is set: most of it, a little way into its own side of the join. */
private const val RING_SHARE = 0.8f

/** A ring's radius in its part's u and v, and how many anchors go round it. */
private const val RING_RADIUS = 0.02f
private const val RING_POINTS = 10

/** How much larger the edit makes the part every join here grows from. */
private const val EDIT_GROWTH = 1.06f

/**
 * Anchors near each join of a character, before and after an edit of the part they grow from: at each join a ring of anchors
 * on the parent and one on the child, each where its part holds most of the surface just inside the join, drawn as ink marks; then the
 * root part made larger, and the same spots again. The marks are taken off and the edit undone after.
 */
class AnchorsLook(work: TestWork, errors: MutableList<String>, folder: File) {
    private val spots = SpotViews(work, errors, folder)
    private val settle = { session: CharacterSession -> work.settle(session) }

    fun keep(session: CharacterSession, name: String) {
        settle(session)
        val surface = session.characterSurface.output.value?.surface ?: return
        val character = session.document.value.character
        val root = character.parts.firstOrNull { it.socket == null } ?: return
        val joins = character.parts.filter { it.socket?.parent == root.id && it.id in surface.parts }
        val marks = joins.flatMap { child -> listOfNotNull(ring(surface, root.id, child.id, "parent"), ring(surface, child.id, child.id, "child")) }
        marks.forEach { (part, mark) -> session.send(SetMark(part, mark.id, mark)) }
        settle(session)
        val placed = checkNotNull(session.placed.output.value)
        val rows = joins.mapNotNull { placed.part(it.id)?.origin }.map { at -> SpotViews.AROUND.take(3).map { Spot(at, SPOT_RADIUS, it) } }
        spots.keep(session, rows, "$name-anchors-before")
        val size = character.resolved(root).params[SurfaceCloseUps.SIZE] ?: checkNotNull(session.builtTemplate(root).defaults[SurfaceCloseUps.SIZE])
        session.send(SetParameter(root.id, SurfaceCloseUps.SIZE, size * EDIT_GROWTH))
        settle(session)
        val moved = checkNotNull(session.placed.output.value)
        val after = joins.mapNotNull { moved.part(it.id)?.origin }.map { at -> SpotViews.AROUND.take(3).map { Spot(at, SPOT_RADIUS, it) } }
        spots.keep(session, after, "$name-anchors-after")
        session.history.undo()
        marks.forEach { (part, mark) -> session.send(SetMark(part, mark.id, null)) }
        settle(session)
    }

    /**
     * A ring of anchors on [part] round where it holds about [RING_SHARE] of the surface nearest the join of [child] to its parent, or
     * null where it holds none there.
     */
    private fun ring(surface: CharacterSurface, part: PartId, child: PartId, side: String): Pair<PartId, Mark>? {
        val mesh = surface.mesh
        val runs = surface.field.runs
        val at = runs.firstOrNull { it.partId == child }?.part?.origin ?: return null
        var best: Pair<Int, Int>? = null
        var least = Float.MAX_VALUE
        for (v in 0 until mesh.vertexCount) for (k in 0 until HELD) {
            val member = mesh.held[v * HELD + k]
            if (member < 0 || runs[member].partId != part || runs[member].patch.name != BODY_PATCH) continue
            val share = mesh.heldShares[v * HELD + k]
            if (share < RING_SHARE - 0.05f || share > RING_SHARE + 0.05f) continue
            val distance = (mesh.position(v) - at).length
            if (distance < least) {
                least = distance
                best = v to k
            }
        }
        val (v, k) = best ?: return null
        val u = mesh.heldU[v * HELD + k]
        val around = mesh.heldV[v * HELD + k]
        val points = List(RING_POINTS + 1) { i ->
            val angle = 2.0 * PI * (i % RING_POINTS) / RING_POINTS
            Anchor(part, BODY_PATCH, u + RING_RADIUS * cos(angle).toFloat(), around + RING_RADIUS * sin(angle).toFloat())
        }
        return part to Mark(MarkId("anchors-${child.value}-$side"), points)
    }
}
