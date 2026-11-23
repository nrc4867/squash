package dev.chieppa.squash.screens.look.recipe

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartChange
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.PoseKey
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SetPoseKeys
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.recipe.build.KeyBodies
import dev.chieppa.squash.recipe.figure.FigureView
import dev.chieppa.squash.recipe.figure.P2
import dev.chieppa.squash.recipe.figure.Silhouette
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.TestWork
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Body units a cell of a posed silhouette's raster spans. */
private const val CELL = 0.0004

/** The name the pose key being fitted takes in the fitting session. */
private const val FITTED = "fitted"

/**
 * Bodies posed through a session of their own, kept while the body is the same document: each asks the session for the body with the
 * changes as a pose key at full weight in the key's pose, and reads the posed surface's silhouette off its triangles.
 */
class PosedKeyBodies(private val library: TemplateLibrary) : KeyBodies, AutoCloseable {
    private var kept: Triple<SquashDocument, CharacterSession, Pair<SessionPose, TestWork>>? = null

    override fun silhouette(body: SquashDocument, pose: Pose, changes: List<PartChange>, rootHeight: Double): Silhouette {
        val (_, session, posing) = kept?.takeIf { it.first === body } ?: open(body)
        val (sessionPose, work) = posing
        session.send(SetPoseKeys(listOf(PoseKey(FITTED, pose.bones, changes))))
        session.send(SetPose(pose))
        work.drain()
        val posed = checkNotNull(sessionPose.surface.posed.output.value?.posed) { "the fitting session posed no surface" }.surface
        val runs = posed.field.runs
        val owners = Array(posed.mesh.vertexCount) { v -> posed.mesh.winners[v].let { w -> runs.getOrNull(w)?.partId?.value } }
        return MeshSilhouette(posed.positions, posed.mesh.triangles, owners, rootHeight)
    }

    private fun open(body: SquashDocument): Triple<SquashDocument, CharacterSession, Pair<SessionPose, TestWork>> {
        close()
        val work = TestWork()
        val session = CharacterSession(body, library, work.scope, work.dispatcher)
        return Triple(body, session, SessionPose(session) to work).also { kept = it }
    }

    override fun close() {
        kept?.third?.second?.close()
        kept = null
    }
}

/**
 * The silhouette a posed mesh casts in each figure view: its triangles filled into a raster of [CELL] cells, made per view when first
 * read. [owners] names the part each vertex's surface belongs to, which a part's silhouette alone keeps the triangles of.
 */
private class MeshSilhouette(
    private val positions: FloatArray,
    private val triangles: IntArray,
    private val owners: Array<String?>,
    private val rootHeight: Double,
) : Silhouette {
    private val rasters = HashMap<FigureView, Raster>()
    private val alone = HashMap<Set<String>, MeshSilhouette>()

    override fun inside(view: FigureView, q: P2): Boolean = rasters.getOrPut(view) { raster(view) }.inside(q)

    /** The triangles at least two of whose corners [parts] own, as a silhouette of their own. */
    override fun alone(parts: Set<String>): Silhouette = alone.getOrPut(parts) {
        val kept = (0 until triangles.size / 3).filter { t -> (0 until 3).count { owners[triangles[t * 3 + it]] in parts } >= 2 }
        MeshSilhouette(positions, IntArray(kept.size * 3) { triangles[kept[it / 3] * 3 + it % 3] }, owners, rootHeight)
    }

    private fun place(view: FigureView, v: Int): P2 {
        val x = positions[v * 3].toDouble()
        val y = positions[v * 3 + 1].toDouble() + rootHeight
        val z = positions[v * 3 + 2].toDouble()
        return if (view == FigureView.FRONT) P2(-z, y) else P2(x, y)
    }

    private fun raster(view: FigureView): Raster {
        val places = Array(positions.size / 3) { place(view, it) }
        val low = P2(places.minOf { it.x } - CELL, places.minOf { it.y } - CELL)
        val high = P2(places.maxOf { it.x } + CELL, places.maxOf { it.y } + CELL)
        val raster = Raster(low, ceil((high.x - low.x) / CELL).toInt() + 1, ceil((high.y - low.y) / CELL).toInt() + 1)
        for (t in 0 until triangles.size / 3) raster.fill(places[triangles[t * 3]], places[triangles[t * 3 + 1]], places[triangles[t * 3 + 2]])
        return raster
    }
}

/** A grid of cells from [low], [columns] by [rows], each marked when a filled triangle covers its middle. */
private class Raster(private val low: P2, private val columns: Int, private val rows: Int) {
    private val cells = BooleanArray(columns * rows)

    fun inside(q: P2): Boolean {
        val c = floor((q.x - low.x) / CELL).toInt()
        val r = floor((q.y - low.y) / CELL).toInt()
        return c in 0 until columns && r in 0 until rows && cells[r * columns + c]
    }

    fun fill(a: P2, b: P2, c: P2) {
        val area = cross(a, b, c)
        if (area == 0.0) return
        val c0 = max(0, floor((min(a.x, min(b.x, c.x)) - low.x) / CELL).toInt())
        val c1 = min(columns - 1, ceil((max(a.x, max(b.x, c.x)) - low.x) / CELL).toInt())
        val r0 = max(0, floor((min(a.y, min(b.y, c.y)) - low.y) / CELL).toInt())
        val r1 = min(rows - 1, ceil((max(a.y, max(b.y, c.y)) - low.y) / CELL).toInt())
        for (r in r0..r1) for (col in c0..c1) {
            val p = P2(low.x + (col + 0.5) * CELL, low.y + (r + 0.5) * CELL)
            val w0 = cross(b, c, p) / area
            val w1 = cross(c, a, p) / area
            val w2 = cross(a, b, p) / area
            if (w0 >= 0 && w1 >= 0 && w2 >= 0) cells[r * columns + col] = true
        }
    }

    private fun cross(a: P2, b: P2, p: P2): Double = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
}
