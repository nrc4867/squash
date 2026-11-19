package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.ShineSource
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.geom.Vec3

/**
 * The band of shine of [source]'s volume over every hair part of [surface]'s one surface, the volume with them: where each vertex the hair
 * holds lies inside the band, read on the volume's sphere where the vertex lies, so the band runs on over the clumps and tufts blended
 * into the volume with no break. Null where the volume is not in the field or the band misses the hair. See HLD: parts, Hair.
 */
fun hairShineLayer(surface: CharacterSurface, source: ShineSource): PaintLayer? {
    val runs = surface.field.runs
    val volume = runs.firstOrNull { it.partId == source.volume }?.part ?: return null
    val axes = PartBuilder.axes(volume.template)
    val hair = BooleanArray(runs.size) { runs[it].part.role == Role.HAIR }
    val mesh = surface.mesh
    val positions = mesh.positions
    val values = FloatArray(mesh.vertexCount) { v ->
        val winner = mesh.winners[v]
        if (winner < 0 || !hair[winner]) {
            OUTSIDE
        } else {
            val p = Vec3(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2])
            source.shine.shine.inside(source.shine.shape.sphereOf(volume.surface.shown(p - volume.origin), axes.along, axes.up, axes.side))
        }
    }
    if (values.none { it >= 0f }) return null
    val triangles = mesh.triangles
    val candidates = (0 until triangles.size / 3).filter { t -> (0..2).any { values[triangles[t * 3 + it]] > OUTSIDE } }.toIntArray()
    return PaintLayer.of(source.slot, surface, values, candidates)
}

/** What a vertex the hair does not hold reads: far outside any band. */
private const val OUTSIDE = -1f
