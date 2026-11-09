package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.ShellInside
import dev.chieppa.squash.geom.Vec3
import kotlin.math.floor

/** What a worn branch covers: a place on the body, by its part, u, v and point in body space. */
fun interface GarmentCover {
    fun covers(part: PartId, u: Float, v: Float, point: Vec3): Boolean
}

/**
 * What [branch] covers. A branch the whole way round covers whatever lies inside it; one over part of the way round covers its own
 * part's surface under it, from its u range and v range.
 */
internal fun coverOf(branch: PlacedPart): GarmentCover {
    val covering = requireNotNull(branch.covering)
    if (covering.allRound) {
        val inside = branch.shellInside()
        return GarmentCover { _, _, _, point -> point in inside }
    }
    return GarmentCover { part, u, v, _ -> part == covering.part && u in covering.from..covering.to && aroundIn(v, covering.fromV, covering.toV) }
}

/** Whether [v] lies from [from] to [to] round a part, reading v round. */
private fun aroundIn(v: Float, from: Float, to: Float): Boolean {
    val ahead = v - from - floor(v - from)
    return ahead <= to - from
}

/** What every garment branch and cuff this character wears covers of the body. */
fun AssembledCharacter.garmentCovers(): List<GarmentCover> = parts.filter { part -> part.covering != null }.map(::coverOf)

/** The inside test of this branch or cuff, in body space. */
internal fun PlacedPart.shellInside(): ShellInside = ShellInside(surface.patches.single(), { origin + surface.shown(it) }, surface::shown)

/** The inside test of [part]'s own swept surface, in body space, or null when it has none. */
internal fun bodyInside(part: PlacedPart): ShellInside? = part.bodyPatch()?.let { ShellInside(it, { p -> part.origin + part.surface.shown(p) }, part.surface::shown) }

/** This part with the skin [covers] says is covered left out of its mesh. */
internal fun PlacedPart.coveredBy(covers: List<GarmentCover>): PlacedPart {
    val id = id ?: return this
    if (covers.isEmpty()) return this
    return hiding { u, v, point -> covers.any { it.covers(id, u, v, point) } }
}

/**
 * This part with every triangle whose corners [hidden] all says are hidden left out of its mesh, the grid and every vertex kept so
 * anchors still land; itself when none is. [hidden] reads each vertex's u and v on its patch and its point in body space.
 */
internal fun PlacedPart.hiding(hidden: (u: Float, v: Float, point: Vec3) -> Boolean): PlacedPart {
    val mesh = surface.mesh
    val flags = BooleanArray(mesh.vertexCount)
    var vertex = 0
    var any = false
    for (patch in surface.patches) for (local in 0 until patch.nu * patch.nv) {
        val u = patch.ringUs[local / patch.nv]
        val v = (local % patch.nv).toFloat() / patch.segments
        flags[vertex] = hidden(u, v, origin + mesh.position(vertex))
        any = any || flags[vertex]
        vertex++
    }
    if (!any) return this
    val indices = mesh.indices
    val kept = ArrayList<Int>(indices.size)
    for (t in 0 until indices.size / 3) {
        val a = indices[t * 3]
        val b = indices[t * 3 + 1]
        val c = indices[t * 3 + 2]
        if (flags[a] && flags[b] && flags[c]) continue
        kept += a
        kept += b
        kept += c
    }
    if (kept.size == indices.size) return this
    val shown = PartSurface(surface.patches, surface.mirrored, RenderMesh(mesh.vertices, kept.toIntArray()), surface.silhouettes, surface.box, surface.extents)
    return showing(shown, turn)
}
