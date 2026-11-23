package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals

/** A jaw whose surface is built turned open: a pose's tip of 0 shuts it, its built tip leaves it as built, and angles read back the same. */
class BuiltJawTest {
    private val jaw = Bone(BoneName(PartId("head"), "jaw"), null, Vec3.ZERO, Vec3.X, 0f, Vec3(0f, 0f, -1f), null, false, built = 35f)

    @Test
    fun theBuiltTipLeavesTheJawAsBuilt() {
        val turn = localTurn(jaw, BoneAngles(35f, 0f, 0f))
        assertEquals(0f, (turn * Vec3.X - Vec3.X).length, 1e-5f)
    }

    @Test
    fun aTipOfZeroTurnsItBackByWhatItWasBuiltAt() {
        val built = localTurn(jaw.copy(built = 0f), BoneAngles(35f, 0f, 0f))
        val shut = localTurn(jaw, BoneAngles(0f, 0f, 0f))
        val front = built * Vec3.X
        assertEquals(0f, (shut * front - Vec3.X).length, 1e-5f)
    }

    @Test
    fun anglesReadBackIncludeTheBuiltTip() {
        val angles = anglesOf(jaw, localTurn(jaw, BoneAngles(12f, 0f, 0f)))
        assertEquals(12f, angles.tip, 1e-3f)
    }
}
