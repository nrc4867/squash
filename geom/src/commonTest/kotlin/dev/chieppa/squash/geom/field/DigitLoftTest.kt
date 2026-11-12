package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Digits joined to a palm's field loft to runs on the one surface, buried where the palm or a neighbour shows it. */
class DigitLoftTest {
    private val axes = PartAxes.of(Vec3.X, Vec3.Y)
    private val radius = 0.03f
    private val digits = listOf(
        DigitAxis(Vec3(0.12f, 0f, 0.035f), Vec3.X, 0.16f, radius),
        DigitAxis(Vec3(0.12f, 0f, -0.035f), Vec3.X, 0.16f, radius),
    )
    private val palm = Blended(Ellipsoid(Vec3(0.06f, 0f, 0f), Vec3(0.09f, 0.04f, 0.08f)))
    private val field = BlendedField(
        listOf(palm),
        digits = FieldDigits(digits.map { Blended(Capsule(it.root - it.direction * 0.04f, it.at(1f), it.radius)) }, 0.02f),
    )
    private val shares = FloatArray(10) { it / 9f }

    private fun run(index: Int) =
        DigitLoft.loft("digit:$index", field, axes, digits, index, shares, FloatArray(shares.size) { 0.4f + 0.6f * it / (shares.size - 1) }, 12, 2f, buriedBefore = 0.1f)

    @Test
    fun everyShownPointLiesOnTheSurface() {
        for (index in digits.indices) {
            val patch = run(index)
            val buried = patch.buried
            for (i in 0 until patch.nu * patch.nv) {
                if (buried?.get(i) == true) continue
                assertTrue(abs(field.value(patch.point(i))) < radius * 0.1f, "point $i of digit $index lies off the surface")
            }
        }
    }

    @Test
    fun ringsBeforeTheBuriedShareAreBuriedWhole() {
        val patch = run(0)
        val buried = requireNotNull(patch.buried)
        for (j in 0 until patch.nv) assertTrue(buried[j])
        assertTrue((0 until patch.nv).any { !buried[(patch.nu - 2) * patch.nv + it] })
    }

    @Test
    fun aRunsMeshSkipsBuriedFacesAndStaysClosedElsewhere() {
        val patch = run(1)
        val mesh = MeshBuilder.build(patch) { 0 }
        assertEquals(emptyList(), MeshInvariants.check(mesh, emptySet()))
        val full = MeshBuilder.build(patch.withBuried(null, null)) { 0 }
        assertTrue(mesh.indices.size < full.indices.size)
    }
}
