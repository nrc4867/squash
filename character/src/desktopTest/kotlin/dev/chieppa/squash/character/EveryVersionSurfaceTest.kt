package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SurfaceMethod
import dev.chieppa.squash.character.surface.SurfaceBuilder
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.surface.SurfaceMesh
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The template versions whose part, built alone as one surface at the coarse cell, is not closed and manifold. An old file that
 * pins one opens rough; each is kept as it ships.
 */
private val ROUGH_VERSIONS: List<String> = emptyList()

/** Every shipped version of a template whose runs are in the field builds a closed, manifold one surface on a character of that part alone. */
class EveryVersionSurfaceTest {
    private val library = TemplateLibrary(shipped.parts, shipped.plans)

    @Test
    fun everyFieldVersionMeshesClosedAndManifoldAtTheCoarseCell() {
        val field = shipped.parts.filter { it.surface == SurfaceMethod.FIELD }
        val rough = field.mapNotNull { template -> problemOf(template)?.let { "${template.id.value} ${template.version}: $it" } }
        assertEquals(ROUGH_VERSIONS, rough.map { it.substringBefore(':') }, rough.joinToString("\n"))
    }

    private fun problemOf(template: PartTemplate): String? {
        val part = AssemblyPart(PartId(template.id.value), template.ref, null, null, null)
        val assembled = assembleNow(listOf(part), { PartData() }, null, library)
        val surface = SurfaceBuilder().build(assembled, dragging = true) ?: return "no surface"
        return closedManifoldProblem(surface.mesh)
    }
}

/** Why [mesh] is not closed and manifold with its triangles facing one way, or null when it is. */
fun closedManifoldProblem(mesh: SurfaceMesh): String? {
    if (mesh.triangleCount == 0) return "no triangles"
    val edges = HashMap<Long, Int>()
    val directed = HashSet<Long>()
    var collapsed = 0
    var repeated = 0
    for (t in 0 until mesh.triangleCount) for (c in 0 until 3) {
        val a = mesh.triangles[t * 3 + c]
        val b = mesh.triangles[t * 3 + (c + 1) % 3]
        if (a == b) collapsed++
        edges.merge(minOf(a, b).toLong() shl Int.SIZE_BITS or maxOf(a, b).toLong(), 1, Int::plus)
        if (!directed.add(a.toLong() shl Int.SIZE_BITS or b.toLong())) repeated++
    }
    val unpaired = edges.values.count { it != 2 }
    return listOfNotNull(
        "$collapsed triangle corners repeat".takeIf { collapsed > 0 },
        "$unpaired edges not on two triangles".takeIf { unpaired > 0 },
        "$repeated edges run the same way twice".takeIf { repeated > 0 },
    ).joinToString("; ").ifEmpty { null }
}
