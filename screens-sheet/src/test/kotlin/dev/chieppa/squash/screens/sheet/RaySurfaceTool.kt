package dev.chieppa.squash.screens.sheet

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.draw.isToolPress
import dev.chieppa.stretch.frame.ViewportOverlay

private const val PARALLEL = 1e-7f

/** A surface tool as far as the panes see one: it takes the free camera and records where each pointer position's ray meets [parts]. */
class RaySurfaceTool(private val parts: () -> List<SheetPart>) : PaneTool {
    /** Each pointer position the tool followed, with the first surface point its ray met, null for a miss. */
    val followed = mutableListOf<Pair<Offset, Vec3?>>()
    var fullView: FullView? = null
        private set

    val hits: List<Vec3> get() = followed.mapNotNull { it.second }

    override val takesFreeCamera: Boolean get() = true

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = Modifier

    override fun fullInput(view: FullView): Modifier = Modifier.pointerInput(this, view.width, view.height) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (!isToolPress(down)) return@awaitEachGesture
            down.consume()
            record(view, down.position)
            followToRelease(down.id, {}) { record(view, it) }
        }
    }

    override fun fullOverlay(view: FullView): ViewportOverlay {
        fullView = view
        return ViewportOverlay.Empty
    }

    private fun record(view: FullView, pixel: Offset) {
        followed += pixel to firstHit(view.ray(pixel), parts())
    }
}

/** The point of [parts] that [ray] meets first, or null when it misses them all. */
fun firstHit(ray: BodyRay, parts: List<SheetPart>): Vec3? {
    val nearest = parts.mapNotNull { part -> nearestAlong(ray, part.mesh, part.origin) }.minOrNull() ?: return null
    return ray.at(nearest)
}

private fun nearestAlong(ray: BodyRay, mesh: RenderMesh, origin: Vec3): Float? {
    var nearest: Float? = null
    for (t in 0 until mesh.indices.size / 3) {
        val a = origin + mesh.position(mesh.indices[t * 3])
        val b = origin + mesh.position(mesh.indices[t * 3 + 1])
        val c = origin + mesh.position(mesh.indices[t * 3 + 2])
        val along = alongToTriangle(ray, a, b, c) ?: continue
        if (nearest == null || along < nearest) nearest = along
    }
    return nearest
}

private fun alongToTriangle(ray: BodyRay, a: Vec3, b: Vec3, c: Vec3): Float? {
    val ab = b - a
    val ac = c - a
    val across = ray.direction cross ac
    val det = ab dot across
    if (det > -PARALLEL && det < PARALLEL) return null
    val fromA = ray.origin - a
    val u = (fromA dot across) / det
    if (u < 0f || u > 1f) return null
    val up = fromA cross ab
    val v = (ray.direction dot up) / det
    if (v < 0f || u + v > 1f) return null
    return ((ac dot up) / det).takeIf { it > 0f }
}
