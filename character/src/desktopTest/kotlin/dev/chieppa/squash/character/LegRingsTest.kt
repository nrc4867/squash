package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.LegRings
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.placedBy
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.surfaceAt
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SAME_POINT = 1e-3f

/** What is read of a leg with nothing built against what its build makes. */
class LegRingsTest {
    private val drawn = PartData(outlines = mapOf(View.SIDE to outline(0f to 0.12f, 0.45f to 0.14f, 0.45f to 0.02f, 0f to -0.04f)))

    private fun assertAgrees(data: PartData, params: Map<String, Float>) {
        val template = template("leg").placedBy(params)
        val whole = data.copy(params = data.params + params)
        val patch = PartBuilder.build(template, whole).first()
        val rings = LegRings(template, whole)
        for ((u, v) in listOf(1f to 0.5f, 0.6f to 0.5f, 0.6f to 0.25f)) {
            val point = assertNotNull(rings.positionAt(u, v))
            assertTrue((patch.positionAt(u, v) - point).length < SAME_POINT, "at $u, $v: $point against ${patch.positionAt(u, v)}")
            val normal = assertNotNull(rings.normalAt(u, v))
            assertTrue((patch.surfaceAt(u, v).normal - normal).length < 2f * SAME_POINT, "normal at $u, $v: $normal")
        }
    }

    @Test
    fun anUndrawnLegReadsAsItBuilds() {
        assertAgrees(PartData(), emptyMap())
        assertAgrees(PartData(), mapOf(PlacementParams.LEAN to 25f))
        assertAgrees(PartData(), mapOf(PlacementParams.OUTWARD to 60f, PlacementParams.LEAN to -60f))
    }

    @Test
    fun aDrawnLegReadsAsItBuilds() {
        assertAgrees(drawn, emptyMap())
        assertAgrees(drawn, mapOf(PlacementParams.OUTWARD to 25f, PlacementParams.LEAN to 25f))
    }

    @Test
    fun aPawsSocketStaysOnTheSameSideOfAnUndrawnLegPastFortyFiveDegrees() {
        val leaned = template("leg").placedBy(mapOf(PlacementParams.OUTWARD to 60f))
        val normal = PartBuilder.build(leaned).first().surfaceAt(1f, 0.5f).normal
        assertTrue((normal - Vec3.X * -1f).length < SAME_POINT, "under a leg is toward its back at any outward angle: $normal")
    }
}
