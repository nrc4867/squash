package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.surface.SurfaceBuilder
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A foot widened to its widest takes a coarser cell, which follows its width, while its toes and talons keep their size: a talon's point
 * then crosses cells thinner than they are. The surface stays closed and manifold, at the full cell and at the coarse one.
 */
class WideFootSurfaceTest {
    private val library = TemplateLibrary(shipped.parts, shipped.plans)

    private fun problemOf(id: String, version: Int, params: Map<String, Float>, dragging: Boolean): String? {
        val template = shipped.parts.single { it.id.value == id && it.version == version }
        val part = AssemblyPart(PartId(id), template.ref, null, null, null)
        val assembled = assembleNow(listOf(part), { PartData(params = params) }, null, library)
        val surface = SurfaceBuilder().build(assembled, dragging) ?: return "no surface"
        return closedManifoldProblem(surface.mesh)
    }

    @Test
    fun theWidestBirdFootAndPawMeshClosedAndManifold() {
        val cases = listOf(
            Triple("foot-bird", 4, mapOf("width" to 0.3f, "toes" to 3f)),
            Triple("foot-bird", 4, mapOf("width" to 0.3f, "toes" to 4f)),
            Triple("paw", 6, mapOf("width" to 0.4f)),
        )
        val rough = cases.flatMap { (id, version, params) ->
            listOf(false, true).mapNotNull { dragging -> problemOf(id, version, params, dragging)?.let { "$id $version $params dragging $dragging: $it" } }
        }
        assertEquals(emptyList(), rough)
    }
}
