package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.character.hair.shellPartId
import dev.chieppa.squash.character.settle
import dev.chieppa.squash.character.surfaceSession
import dev.chieppa.squash.document.HairShell
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetHairShell
import dev.chieppa.squash.document.SetViewKeys
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.ViewKeys
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** How far, as a share of the head's frame length, a shell vertex's change may lie beyond the changes of the skin round it. */
private const val GAP_KEPT = 0.005f

/** How far round a shell vertex, as a share of the head's frame length, the skin whose changes it must keep within lies. */
private const val NEAR_SHARE = 0.06f

/** How far, as a share of the head's frame length, the furthest shell vertex moves at least under a turning key. */
private const val MOVES = 0.02f

/** A short-hair shell on a round head whose face follows the camera: under every key direction it rides the head's surface under it. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShellShapesTest {
    private val headId = PartId("head")
    private val head = PartInstance(headId, TemplateRef(TemplateId("head-round"), 2), role = Role.HEAD)

    @Test
    fun aShellMovesWithTheHeadsSurfaceUnderAViewKey() = runTest {
        val session = surfaceSession(head)
        session.send(SetHairShell(headId, HairShell(headId)))
        session.send(SetViewKeys(ViewKeys(faceFollow = 1f)))
        session.settle()
        val character = assertNotNull(session.assembly.output.value)
        val surface = assertNotNull(session.characterSurface.output.value?.surface)
        val shapes = ViewKeyShapes.of(character, surface, session.document.value.character.viewKeys)
        val shell = assertNotNull(character.part(shellPartId(headId)))
        val deltas = assertNotNull(shapes.parts[shellPartId(headId)], "the key moves the shell").deltas
        val length = shapes.heads.getValue(headId).length
        val runs = surface.field.runs
        val skin = (0 until surface.mesh.vertexCount).filter { v -> surface.mesh.winners[v].let { it >= 0 && runs[it].partId == headId } }
        val slot = shapes.moved.withIndex().associate { (i, v) -> v to i }
        val points = shell.surface.mesh.let { mesh -> List(mesh.vertexCount) { shell.origin + shell.surface.shown(mesh.vertices.vec(it * RenderMesh.FLOATS_PER_VERTEX)) } }
        val samples = points.indices.filter { it % 97 == 0 }
        var furthest = 0f
        val around = samples.associateWith { v ->
            skin.filter { (surface.render.vertices.vec(it * RenderMesh.FLOATS_PER_VERTEX) - points[v]).length < NEAR_SHARE * length }
        }
        for ((d, delta) in deltas.withIndex()) {
            delta ?: continue
            for (v in samples) {
                val moves = shell.surface.shown(delta.vec(v * DELTA_FLOATS))
                furthest = maxOf(furthest, moves.length)
                val skinMoves = around.getValue(v).map { s -> slot[s]?.let { shapes.surfaceDeltas[d].vec(it * DELTA_FLOATS) } ?: Vec3.ZERO }
                if (skinMoves.isEmpty()) continue
                val spread = skinMoves.maxOf { a -> skinMoves.maxOf { b -> (a - b).length } }
                val apart = skinMoves.minOf { (moves - it).length }
                assertTrue(apart <= spread + GAP_KEPT * length, "direction $d vertex $v moves as the skin round it: $moves, the skin's within $spread of each other, $apart from the nearest")
            }
        }
        assertTrue(furthest > MOVES * length, "the shell turns with the face: $furthest")
    }
}
