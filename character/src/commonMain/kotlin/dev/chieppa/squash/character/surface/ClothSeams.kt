package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.CurvePoint
import dev.chieppa.squash.geom.surface.HELD
import dev.chieppa.squash.geom.surface.SurfaceMesh

/** How much of two areas' cloth together a seam's place holds at least, so a seam is not read where neither area is painted. */
private const val SEAM_PRESENCE = 0.35f

/** How much of a vertex its run holds at least for a seam dropped down that run to be read there. */
private const val DROP_SHARE = 0.5f

/**
 * Where the areas of [worn]'s garment meet, read on a garment surface meshed from [field]: for each area but the last, one value a
 * vertex, how much more of that area's cloth than of any later area's lies there, share-weighted over the runs it holds; zero along the
 * seam between them, NaN where no run holding the vertex is painted by either, or where they hold too little cloth there.
 */
class ClothSeams(private val field: CharacterField, private val worn: WornCover) {
    private val areas = worn.garment.areas.size

    /** The seam values at every vertex of [mesh], one array an area but the last, then one for each seam the garment drops. */
    fun values(mesh: SurfaceMesh): List<FloatArray> = areaValues(mesh) + dropValues(mesh)

    /**
     * For each run whose cloth drops its seam, one value a vertex: how far past the seam along the run it lies, over the runs it holds that
     * are that run; zero along the seam, NaN where the vertex lies mostly on other runs.
     */
    private fun dropValues(mesh: SurfaceMesh): List<FloatArray> {
        val drops = field.runs.indices.mapNotNull { r -> field.runs[r].takeIf { it.first }?.let { worn.eases[it.partId]?.drop }?.let { r to it.u } }
        return drops.map { (run, u) ->
            FloatArray(mesh.vertexCount) { v ->
                var sum = 0f
                var total = 0f
                for (h in 0 until HELD) {
                    if (mesh.held[v * HELD + h] != run) continue
                    val share = mesh.heldShares[v * HELD + h]
                    sum += share * (mesh.heldU[v * HELD + h] - u)
                    total += share
                }
                if (total < DROP_SHARE) Float.NaN else sum / total
            }
        }
    }

    private fun areaValues(mesh: SurfaceMesh): List<FloatArray> {
        if (areas < 2) return emptyList()
        val cloths = Array(field.runs.size) { r -> field.runs[r].takeIf { it.first }?.let { worn.clothOf(it.partId) }?.takeIf { it.areaCount == areas } }
        return (0 until areas - 1).map { k ->
            FloatArray(mesh.vertexCount) { v ->
                var sum = 0f
                var presence = 0f
                var total = 0f
                for (h in 0 until HELD) {
                    val member = mesh.held[v * HELD + h]
                    if (member < 0) continue
                    val cloth = cloths[member] ?: continue
                    val u = mesh.heldU[v * HELD + h]
                    val around = mesh.heldV[v * HELD + h]
                    val laterPaints = (k + 1 until areas).any(cloth::paints)
                    if (!cloth.paints(k) && !laterPaints) continue
                    val own = cloth.areaAt(k, u, around)
                    var later = 0f
                    for (j in k + 1 until areas) later = maxOf(later, cloth.areaAt(j, u, around))
                    val share = mesh.heldShares[v * HELD + h]
                    sum += share * (own - later)
                    presence += share * (own + later)
                    total += share
                }
                if (total <= 0f || presence / total < SEAM_PRESENCE) Float.NaN else sum / total
            }
        }
    }
}

/** The seams of [values], each where it is zero over triangles of [surface] the garment covers whole, eased. */
internal fun seamsOf(surface: CharacterSurface, coverage: FloatArray, values: List<FloatArray>): List<List<CurvePoint>> {
    val t = surface.mesh.triangles
    return values.flatMap { seam ->
        val clean = FloatArray(seam.size) { if (seam[it].isNaN()) 1f else seam[it] }
        surface.lines.zeroCurves(clean) { tri ->
            (0 until 3).all { coverage[t[tri * 3 + it]] >= 0f && !seam[t[tri * 3 + it]].isNaN() }
        }.map(::eased)
    }
}
