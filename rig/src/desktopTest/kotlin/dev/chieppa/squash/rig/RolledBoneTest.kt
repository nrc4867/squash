package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.placedBy
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

private const val CLOSE = 1e-4f

/** A rolled part's hinges turn with it about its axis, so its bones bend the same way across the part however it is rolled. */
class RolledBoneTest {
    private fun close(expected: Vec3, actual: Vec3, what: String) = assertTrue((expected - actual).length < CLOSE, "$what: expected $expected, got $actual")

    @Test
    fun hingesTurnWithTheRoll() {
        val tube = checkNotNull(shippedLibrary.latest(TemplateId("tube")))
        val rest = PartBuilder.axes(tube)
        for (roll in listOf(35f, 90f, -120f)) {
            val rolled = PartBuilder.axes(tube.placedBy(mapOf(PlacementParams.ROLL to roll)))
            val angle = roll * PI.toFloat() / 180f
            val turnedUp = rest.up * cos(angle) + rest.side * sin(angle)
            val turnedSide = rest.side * cos(angle) - rest.up * sin(angle)
            close(rest.along, hingeDirection(Hinge.Z, rolled, false), "z stays along the part at roll $roll")
            close(turnedUp, hingeDirection(Hinge.Y, rolled, false), "y turns with the up at roll $roll")
            close(turnedSide, hingeDirection(Hinge.X, rolled, false), "x turns with the side at roll $roll")
        }
    }
}
