package dev.chieppa.squash.character

import dev.chieppa.squash.geom.PartMesh
import kotlin.math.roundToInt

/** What a golden fixture keeps of a vertex's position: one step, as a fraction of the axis length. */
const val FIXTURE_STEP = 1e-4f

/**
 * A mesh as fixture text: for each patch its name and counts, then a line per ring of `x,y,z` whole numbers of
 * [FIXTURE_STEP], which reads the same across runs and platforms where printed floats might not.
 */
fun templateFixture(mesh: PartMesh): String = buildString {
    for (patchMesh in mesh.patches) {
        val patch = patchMesh.patch
        append("${patch.name} rings=${patch.nu} segments=${patch.nv} triangles=${patchMesh.triangleCount}\n")
        for (ring in 0 until patch.nu) {
            append(
                (0 until patch.nv).joinToString(" ") { segment ->
                    val p = patch.point(patch.index(ring, segment))
                    "${(p.x / FIXTURE_STEP).roundToInt()},${(p.y / FIXTURE_STEP).roundToInt()},${(p.z / FIXTURE_STEP).roundToInt()}"
                },
            )
            append("\n")
        }
    }
}
