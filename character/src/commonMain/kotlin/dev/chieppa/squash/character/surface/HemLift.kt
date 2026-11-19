package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.surface.HELD

/** How far a garment stands off the skin at least along its hems, in the body's cells, so the cloth never meets the skin it ends on. */
internal const val HEM_LIFT_CELLS = 0.15f

/** How far past the skin a garment's hem keeps showing, in the body's cells, over which its least lift fades out. */
private const val LIFT_FADE_CELLS = 1f

/** How wide a hem's roll is, in the roll's own height, beyond the garment's cell. */
private const val ROLL_WIDTH_HEIGHTS = 2.5f

/** How much of a hem's roll's width stands at its full height before it rounds down to the cloth. */
private const val ROLL_FLAT_SHARE = 0.35f

/**
 * How far each vertex of a garment's grown [surface] is lifted off along its normal near the garment's hems, from its [coverage], the
 * distance inside the cloth from the hem: by at least [HEM_LIFT_CELLS] of the body's [bodyCell] less the ease there, over the band of
 * skin the garment keeps shown under its hem (see [HiddenSkin]), fading out past it; and by the garment's hem roll, full at the hem and
 * rounding down to the cloth over its width. Zero everywhere for a garment whose ease clears the least lift and with no roll.
 */
internal fun hemLifts(surface: CharacterSurface, cover: WornCover, coverage: FloatArray, bodyCell: Float): FloatArray? {
    val mesh = surface.mesh
    val least = bodyCell * HEM_LIFT_CELLS
    val roll = cover.garment.hemRoll
    val eases = Array(surface.field.runs.size) { r -> surface.field.runs[r].takeIf { it.first }?.let { cover.branchOf(it.partId)?.ease } }
    if (roll <= 0f && eases.all { it == null || it >= least }) return null
    val rollWidth = roll * ROLL_WIDTH_HEIGHTS + mesh.cell
    val lifts = FloatArray(mesh.vertexCount)
    var any = false
    for (v in 0 until mesh.vertexCount) {
        val ease = easeAt(mesh.held, mesh.heldShares, v, eases)
        val inside = maxOf(coverage[v], 0f)
        val shown = bodyCell * HIDING_CELLS + ease * SEEN_UNDER_EASES
        val lift = maxOf(0f, least - ease) * fade(inside, shown, shown + bodyCell * LIFT_FADE_CELLS) +
            roll * fade(inside, rollWidth * ROLL_FLAT_SHARE, rollWidth)
        lifts[v] = lift
        if (lift > 0f) any = true
    }
    return lifts.takeIf { any }
}

/** [surface] with each of its vertices moved out along its normal by [lifts], a quad's middle by the mean of its corners'. */
internal fun lifted(surface: CharacterSurface, lifts: FloatArray): CharacterSurface {
    val corners = surface.mesh.vertexCount
    val normals = surface.lineNormals
    val positions = surface.positions.copyOf()
    for (v in 0 until corners) for (axis in 0 until 3) positions[v * 3 + axis] += normals[v * 3 + axis] * lifts[v]
    val render = surface.render
    val width = RenderMesh.FLOATS_PER_VERTEX
    val vertices = render.vertices.copyOf()
    val all = FloatArray(render.vertexCount)
    lifts.copyInto(all, endIndex = minOf(corners, all.size))
    val indices = render.indices
    if (render.vertexCount > corners) for (tri in 0 until indices.size / 3 step 4) {
        val middle = indices[tri * 3 + 2]
        if (middle < corners) continue
        var sum = 0f
        for (k in 0 until 4) sum += lifts[indices[(tri + k) * 3]]
        all[middle] = sum / 4f
    }
    for (v in 0 until render.vertexCount) {
        if (all[v] == 0f) continue
        for (axis in 0 until 3) vertices[v * width + axis] += vertices[v * width + RenderMesh.NORMAL_OFFSET_FLOATS + axis] * all[v]
    }
    return surface.movedTo(RenderMesh(vertices, indices), positions, normals)
}

/** The ease of the branches over the runs vertex [v] holds, share-weighted; [eases] by run, null for a run the garment does not cover. */
private fun easeAt(held: IntArray, shares: FloatArray, v: Int, eases: Array<Float?>): Float {
    var sum = 0f
    var total = 0f
    for (k in 0 until HELD) {
        val member = held[v * HELD + k]
        if (member < 0) continue
        val ease = eases[member] ?: continue
        sum += shares[v * HELD + k] * ease
        total += shares[v * HELD + k]
    }
    return if (total > 0f) sum / total else 0f
}

/** 1 up to [full], easing down to 0 at [none]. */
private fun fade(at: Float, full: Float, none: Float): Float {
    if (at <= full) return 1f
    if (at >= none) return 0f
    val t = (at - full) / (none - full)
    return 1f - t * t * (3f - 2f * t)
}
