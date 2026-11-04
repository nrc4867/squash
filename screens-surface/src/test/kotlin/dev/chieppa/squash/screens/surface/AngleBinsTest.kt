package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals

private val FRONT = CameraBin("front")
private val QUARTER = CameraBin("three-quarter")
private val SIDE = CameraBin("side")

class AngleBinsTest {
    private val bins = AngleBins(listOf(FRONT, QUARTER, SIDE), listOf(35f, 70f), 6f)

    @Test
    fun withNothingShownAnAngleFallsInItsBin() {
        assertEquals(FRONT, bins.binFor(0f))
        assertEquals(FRONT, bins.binFor(34.9f))
        assertEquals(QUARTER, bins.binFor(35f))
        assertEquals(QUARTER, bins.binFor(69.9f))
        assertEquals(SIDE, bins.binFor(90f))
    }

    @Test
    fun aBinHoldsUntilTheAngleHasGonePastItsEdgeByTheHysteresis() {
        assertEquals(FRONT, bins.binFor(40f, FRONT))
        assertEquals(QUARTER, bins.binFor(41.5f, FRONT))
        assertEquals(QUARTER, bins.binFor(30f, QUARTER))
        assertEquals(FRONT, bins.binFor(28f, QUARTER))
        assertEquals(SIDE, bins.binFor(65f, SIDE))
        assertEquals(QUARTER, bins.binFor(63f, SIDE))
    }

    @Test
    fun aViewLingeringAtAnEdgeDoesNotFlip() {
        var held = bins.binFor(34f)
        val shown = listOf(33f, 36f, 34f, 37f, 35f, 38f).map { degrees -> bins.binFor(degrees, held).also { held = it } }
        assertEquals(List(6) { FRONT }, shown)
    }

    @Test
    fun degreesAreMeasuredFromTheForwardAxis() {
        assertEquals(0f, degreesBetween(Vec3.X, Vec3(2f, 0f, 0f)), 0.01f)
        assertEquals(90f, degreesBetween(Vec3.X, Vec3.Z), 0.01f)
        assertEquals(45f, degreesBetween(Vec3.X, Vec3(1f, 0f, 1f)), 0.01f)
    }
}
