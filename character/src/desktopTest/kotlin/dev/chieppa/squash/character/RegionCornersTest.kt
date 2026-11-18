package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.character.build.ResolvedRegion
import dev.chieppa.squash.character.build.regionCorners
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals

class RegionCornersTest {
    private val head = PartId("head")

    private fun region(id: String, vararg uv: Pair<Float, Float>, corners: Set<Int> = emptySet()) = ResolvedRegion(
        RegionId(id),
        uv.map { (u, v) -> ResolvedPoint(Anchor(head, BODY_PATCH, u, v), Vec3.ZERO, Vec3.ZERO) },
        SlotName(id),
        ink = false,
        corners = corners,
    )

    private val edge = arrayOf(0.35f to 0.38f, 0.45f to 0.43f, 0.5f to 0.5f, 0.45f to 0.57f, 0.35f to 0.62f)

    @Test
    fun twoRegionsThroughOneEdgeTurnSharplyWhereTheyPart() {
        val muzzle = region("muzzle", *edge, 0.15f to 0.64f, 0.15f to 0.36f)
        val top = region("top", *edge.reversedArray(), 0.6f to 0.32f, 0.72f to 0.5f, 0.6f to 0.68f)
        val corners = regionCorners(listOf(muzzle, top))
        assertEquals(setOf(0, 4), corners[muzzle])
        assertEquals(setOf(0, 4), corners[top])
    }

    @Test
    fun aRegionAloneKeepsOnlyItsOwnCorners() {
        val alone = region("alone", 0.3f to 0.5f, 0.5f to 0.75f, 0.7f to 0.5f, 0.5f to 0.25f, corners = setOf(2))
        assertEquals(mapOf(alone to setOf(2)), regionCorners(listOf(alone)))
    }
}
